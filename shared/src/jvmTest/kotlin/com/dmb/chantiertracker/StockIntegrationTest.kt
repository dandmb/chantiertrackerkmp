package com.dmb.chantiertracker

import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.validation_amount_two_decimals
import com.dmb.chantiertracker.data.local.db.SyncStatus
import com.dmb.chantiertracker.domain.model.CreateConsumptionLineInput
import com.dmb.chantiertracker.domain.model.CreateProjectInput
import com.dmb.chantiertracker.domain.model.CreatePurchaseLineInput
import com.dmb.chantiertracker.domain.model.CreateStageInput
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.UpdatePurchaseLineInput
import com.dmb.chantiertracker.presentation.logs.availableCeiling
import com.dmb.chantiertracker.support.DeviceStack
import com.dmb.chantiertracker.support.DisposableAccounts
import com.dmb.chantiertracker.support.IntegrationBackend
import com.dmb.chantiertracker.support.retryingOnRateLimit
import com.dmb.chantiertracker.support.signInForTheFirstTime
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Campagne QA, groupe C — le stock (P5), contre un vrai backend. Même convention que les groupes
 * A et B : désactivé par défaut, comptes jetables créés par le super-admin, assertions =
 * comportement attendu, tout ce qui est observé est imprimé. Chaque scénario compare le stock
 * affiché par l'app (calculé depuis les lignes locales) à celui du serveur (`GET /projects/{id}/stock`).
 */
class StockIntegrationTest {

    private val accounts = DisposableAccounts()
    private val devices = mutableListOf<DeviceStack>()

    @AfterTest
    fun cleanUp() {
        devices.forEach { it.close() }
        accounts.close()
    }

    private fun device() = DeviceStack().also { devices += it }

    private fun runScenario(block: suspend () -> Unit) = runBlocking {
        if (!IntegrationBackend.enabled || !IntegrationBackend.adminConfigured) {
            println("Scénario ignoré (integrationTests + identifiants super-admin requis).")
            return@runBlocking
        }
        block()
    }

    private fun today(): LocalDate = LocalDate.now(ZoneId.of("Europe/Paris"))

    private suspend fun DeviceStack.signedInAs(prefix: String, name: String): DisposableAccounts.Account {
        val account = accounts.create(prefix, name)
        auth.signInForTheFirstTime(account, "QaPassword1234!")
        return account
    }

    private suspend fun DeviceStack.entryOf(dayLocalId: String, type: EntryType): String =
        logs.observeLog(dayLocalId).first()!!.entries.single { it.type == type }.localId

    private suspend fun DeviceStack.appStock(projectLocalId: String): Map<String, Double> =
        materials.observeStock(projectLocalId).first().materials.associate { it.materialName to it.available }

    private suspend fun DeviceStack.serverStock(projectLocalId: String): Map<String, Double> {
        val serverId = db.projectDao().findByLocalId(projectLocalId)!!.serverId!!
        val page = Json.parseToJsonElement(client.get("projects/$serverId/stock").bodyAsText()).jsonObject
        return page["content"]!!.jsonArray.associate {
            it.jsonObject["materialName"]!!.jsonPrimitive.content to it.jsonObject["available"]!!.jsonPrimitive.double
        }
    }

    private suspend fun DeviceStack.compare(label: String, projectLocalId: String): Pair<Map<String, Double>, Map<String, Double>> {
        val app = appStock(projectLocalId)
        val server = serverStock(projectLocalId)
        println("$label — stock app=$app ; serveur=$server")
        return app to server
    }

    private suspend fun DeviceStack.newSite(name: String): Pair<String, String> {
        val project = projects.createProject(CreateProjectInput(name, null, "Nîmes", "EUR", "Europe/Paris"))
        val stage = stages.createStage(CreateStageInput(project, "Gros œuvre", null, null, null, null))
        sync.syncNow()
        return project to stage
    }

    private class Site(val owner: DeviceStack, val supervisor: DeviceStack, val ownerProject: String, val ownerStage: String,
                       val supervisorProject: String, val supervisorStage: String)

    private suspend fun aSiteWithASupervisor(tag: String): Site {
        val owner = device()
        owner.signedInAs("qa-c-$tag-owner", "QA C Propriétaire $tag")
        val supervisor = device()
        val supervisorAccount = supervisor.signedInAs("qa-c-$tag-super", "QA C Superviseur $tag")
        val (project, stage) = owner.newSite("QA C $tag")
        owner.invitations.invite(project, supervisorAccount.email)
        supervisor.invitations.acceptInvitation(supervisor.invitations.listIncomingInvitations().single().token)
        supervisor.sync.syncNow()
        val supervisorProject = supervisor.projects.observeProjects().first().single().localId
        supervisor.sync.syncProject(supervisorProject)
        val supervisorStage = supervisor.stages.observeStages(supervisorProject).first().single().localId
        return Site(owner, supervisor, project, stage, supervisorProject, supervisorStage)
    }

    // ─── P5 — a real day: purchase, stock, consumption, stock exhausted ─────

    @Test
    fun p5_a_purchase_then_consumptions_down_to_zero_keep_the_app_stock_equal_to_the_server() = runScenario {
        val owner = device()
        owner.signedInAs("qa-c-p5", "QA C P5")
        val (project, stage) = owner.newSite("QA C P5")
        val date = today().toString()

        val purchase = owner.entryOf(owner.logs.createPurchaseEntry(stage, date), EntryType.PURCHASE)
        val cement = owner.materials.createMaterial(project, "Ciment", "sac")
        val sand = owner.materials.createMaterial(project, "Sable", "t")
        owner.purchaseLines.createLine(purchase, CreatePurchaseLineInput(cement.localId, 10.0, 6.5, "Négoce"))
        owner.purchaseLines.createLine(purchase, CreatePurchaseLineInput(sand.localId, 2.5, 40.0, "Carrière"))
        owner.sync.syncNow()
        val (afterPurchase, serverAfterPurchase) = owner.compare("P5 achat", project)
        assertEquals(serverAfterPurchase, afterPurchase)

        val work = owner.entryOf(owner.logs.createWorkEntry(stage, date), EntryType.WORK)
        val consumption = owner.consumptionLines.createLine(work, CreateConsumptionLineInput(cement.localId, 4.0))
        owner.consumptionLines.createLine(work, CreateConsumptionLineInput(sand.localId, 0.75))
        owner.sync.syncNow()
        val (afterConsumption, serverAfterConsumption) = owner.compare("P5 consommation", project)
        assertEquals(mapOf("Ciment" to 6.0, "Sable" to 1.75), serverAfterConsumption)
        assertEquals(serverAfterConsumption, afterConsumption)
        println("P5 — plafond du formulaire de consommation pour le ciment : ${availableCeiling(owner.materials.observeStock(project).first().materials, cement.localId, null)}")

        owner.consumptionLines.updateLine(consumption, com.dmb.chantiertracker.domain.model.UpdateConsumptionLineInput(10.0))
        owner.sync.syncNow()
        val (exhausted, serverExhausted) = owner.compare("P5 stock épuisé", project)
        assertEquals(0.0, serverExhausted["Ciment"])
        assertEquals(serverExhausted, exhausted)
    }

    // ─── what the app knows of the stock vs. what the server holds ───────────

    @Test
    fun c_stock_bought_on_a_day_this_device_never_opened_is_still_counted() = runScenario {
        val site = aSiteWithASupervisor("seen")
        val yesterday = today().minusDays(1).toString()
        val purchase = site.owner.entryOf(site.owner.logs.createPurchaseEntry(site.ownerStage, yesterday), EntryType.PURCHASE)
        val cement = site.owner.materials.createMaterial(site.ownerProject, "Ciment", "sac")
        site.owner.purchaseLines.createLine(purchase, CreatePurchaseLineInput(cement.localId, 12.0, 6.5, "Négoce"))
        site.owner.sync.syncNow()

        site.supervisor.sync.syncNow()
        site.supervisor.sync.syncProject(site.supervisorProject)
        val (app, server) = site.supervisor.compare("C stock non ouvert (superviseur, projet synchronisé, journée d'hier jamais ouverte)", site.supervisorProject)
        val supervisorCement = site.supervisor.materials.observeMaterials(site.supervisorProject).first().single { it.name == "Ciment" }
        val ceiling = availableCeiling(site.supervisor.materials.observeStock(site.supervisorProject).first().materials, supervisorCement.localId, null)
        println("C stock non ouvert — plafond du formulaire de consommation du superviseur : $ceiling (serveur : ${server["Ciment"]})")

        val day = site.supervisor.logs.observeLogs(site.supervisorStage).first().single()
        site.supervisor.sync.syncLog(day.localId)
        site.supervisor.compare("C stock non ouvert — après ouverture de la journée d'hier", site.supervisorProject)

        assertEquals(server, app, "le stock affiché au superviseur doit être celui du projet, pas celui des journées ouvertes sur son appareil")
    }

    @Test
    fun c_a_second_device_of_the_same_owner_shows_the_project_stock() = runScenario {
        val phone = device()
        val account = phone.signedInAs("qa-c-second", "QA C Deux appareils")
        val (project, stage) = phone.newSite("QA C deux appareils")
        val purchase = phone.entryOf(phone.logs.createPurchaseEntry(stage, today().toString()), EntryType.PURCHASE)
        val cement = phone.materials.createMaterial(project, "Ciment", "sac")
        phone.purchaseLines.createLine(purchase, CreatePurchaseLineInput(cement.localId, 8.0, 6.5, null))
        phone.sync.syncNow()

        val tablet = device()
        retryingOnRateLimit { tablet.auth.login(account.email, "QaPassword1234!") }
        tablet.sync.syncNow()
        val tabletProject = tablet.projects.observeProjects().first().single().localId
        tablet.sync.syncProject(tabletProject)
        val (app, server) = tablet.compare("C second appareil (projet synchronisé, journée du jour jamais ouverte)", tabletProject)

        assertEquals(server, app, "un second appareil doit afficher le stock du projet")
    }

    @Test
    fun c_a_purchase_edited_below_what_was_consumed_is_refused_and_the_app_stock_follows_the_server() = runScenario {
        val owner = device()
        owner.signedInAs("qa-c-edit", "QA C Modification")
        val (project, stage) = owner.newSite("QA C modification")
        val date = today().toString()
        val purchase = owner.entryOf(owner.logs.createPurchaseEntry(stage, date), EntryType.PURCHASE)
        val cement = owner.materials.createMaterial(project, "Ciment", "sac")
        val line = owner.purchaseLines.createLine(purchase, CreatePurchaseLineInput(cement.localId, 10.0, 6.5, null))
        val work = owner.entryOf(owner.logs.createWorkEntry(stage, date), EntryType.WORK)
        owner.consumptionLines.createLine(work, CreateConsumptionLineInput(cement.localId, 4.0))
        owner.sync.syncNow()

        owner.purchaseLines.updateLine(line, UpdatePurchaseLineInput(3.0, 6.5, null))
        owner.sync.syncNow()
        val row = owner.db.purchaseLineDao().findByLocalId(line)
        println("C modification — ligne d'achat ramenée à 3 sous les 4 consommés : ${row?.syncStatus}/${row?.lastSyncError} quantité locale=${row?.quantity}")
        val (app, server) = owner.compare("C modification refusée", project)

        assertEquals(server, app, "une modification refusée ne doit pas laisser un stock affiché différent du serveur")
    }

    @Test
    fun c_deleting_a_consumed_purchase_line_is_refused_and_the_stock_comes_back() = runScenario {
        val owner = device()
        owner.signedInAs("qa-c-delete", "QA C Suppression")
        val (project, stage) = owner.newSite("QA C suppression")
        val date = today().toString()
        val purchase = owner.entryOf(owner.logs.createPurchaseEntry(stage, date), EntryType.PURCHASE)
        val cement = owner.materials.createMaterial(project, "Ciment", "sac")
        val line = owner.purchaseLines.createLine(purchase, CreatePurchaseLineInput(cement.localId, 10.0, 6.5, null))
        val work = owner.entryOf(owner.logs.createWorkEntry(stage, date), EntryType.WORK)
        owner.consumptionLines.createLine(work, CreateConsumptionLineInput(cement.localId, 4.0))
        owner.sync.syncNow()

        owner.purchaseLines.deleteLine(line)
        owner.compare("C suppression — juste après la suppression locale", project)
        owner.sync.syncNow()
        val row = owner.db.purchaseLineDao().findByLocalId(line)
        println("C suppression — ligne après refus : ${row?.syncStatus}/${row?.pendingOp}/${row?.lastSyncError} quantité=${row?.quantity}")
        val (app, server) = owner.compare("C suppression refusée", project)

        assertEquals(server, app)
    }

    @Test
    fun c_a_stage_deleted_by_the_owner_gives_its_stock_back_on_the_supervisor_device() = runScenario {
        val site = aSiteWithASupervisor("stage")
        val secondStage = site.owner.stages.createStage(CreateStageInput(site.ownerProject, "Second œuvre", null, null, null, null))
        site.owner.sync.syncNow()
        val date = today().toString()
        val purchase = site.owner.entryOf(site.owner.logs.createPurchaseEntry(secondStage, date), EntryType.PURCHASE)
        val cement = site.owner.materials.createMaterial(site.ownerProject, "Ciment", "sac")
        site.owner.purchaseLines.createLine(purchase, CreatePurchaseLineInput(cement.localId, 5.0, 6.5, null))
        val firstPurchase = site.owner.entryOf(site.owner.logs.createPurchaseEntry(site.ownerStage, date), EntryType.PURCHASE)
        site.owner.purchaseLines.createLine(firstPurchase, CreatePurchaseLineInput(cement.localId, 3.0, 6.5, null))
        site.owner.sync.syncNow()

        site.supervisor.sync.syncProject(site.supervisorProject)
        for (stage in site.supervisor.stages.observeStages(site.supervisorProject).first()) {
            site.supervisor.sync.syncStage(stage.localId)
            site.supervisor.logs.observeLogs(stage.localId).first().forEach { site.supervisor.sync.syncLog(it.localId) }
        }
        site.supervisor.compare("C étape — avant suppression (journées ouvertes)", site.supervisorProject)

        site.owner.stages.deleteStage(secondStage)
        site.owner.sync.syncNow()
        site.supervisor.sync.syncNow()
        site.supervisor.sync.syncProject(site.supervisorProject)
        val (app, server) = site.supervisor.compare("C étape — après suppression par le propriétaire", site.supervisorProject)

        assertEquals(mapOf("Ciment" to 3.0), server)
        assertEquals(server, app)
    }

    @Test
    fun c_the_same_material_created_offline_on_two_devices() = runScenario {
        val site = aSiteWithASupervisor("dup")
        val ownerSecondStage = site.owner.stages.createStage(CreateStageInput(site.ownerProject, "Second œuvre", null, null, null, null))
        site.owner.sync.syncNow()
        val date = today().toString()

        site.owner.goOffline()
        site.supervisor.goOffline()
        val ownerEntry = site.owner.entryOf(site.owner.logs.createPurchaseEntry(ownerSecondStage, date), EntryType.PURCHASE)
        val ownerSand = site.owner.materials.createMaterial(site.ownerProject, "Sable", "t")
        site.owner.purchaseLines.createLine(ownerEntry, CreatePurchaseLineInput(ownerSand.localId, 2.0, 40.0, null))
        val supervisorEntry = site.supervisor.entryOf(site.supervisor.logs.createPurchaseEntry(site.supervisorStage, date), EntryType.PURCHASE)
        val supervisorSand = site.supervisor.materials.createMaterial(site.supervisorProject, "Sable", "t")
        val supervisorLine = site.supervisor.purchaseLines.createLine(supervisorEntry, CreatePurchaseLineInput(supervisorSand.localId, 3.0, 40.0, null))

        site.owner.goOnline()
        site.owner.sync.syncNow()
        site.supervisor.goOnline()
        site.supervisor.sync.syncNow()
        site.supervisor.sync.syncNow()
        site.supervisor.sync.syncProject(site.supervisorProject)

        val material = site.supervisor.db.materialDao().findByLocalId(supervisorSand.localId)
        val line = site.supervisor.db.purchaseLineDao().findByLocalId(supervisorLine)
        val materials = site.supervisor.materials.observeMaterials(site.supervisorProject).first().map { it.name }
        println("C doublon — matériau du superviseur : ${material?.syncStatus}/${material?.lastSyncError} serverId=${material?.serverId} ; sa ligne : ${line?.syncStatus}/${line?.lastSyncError} ; matériaux listés=$materials")
        val (app, server) = site.supervisor.compare("C doublon", site.supervisorProject)

        assertEquals(SyncStatus.SYNCED, line?.syncStatus, "l'achat du superviseur doit rejoindre le « Sable » déjà créé par le propriétaire")
        assertEquals(listOf("Sable"), materials, "un seul « Sable » dans le projet")
        assertEquals(server, app)
    }

    @Test
    fun c_a_quantity_with_three_decimals_is_refused_by_the_form_and_two_decimals_reach_the_server() = runScenario {
        val refusal = com.dmb.chantiertracker.presentation.logs.validateRequiredQuantity("2,675")
        println("C décimales — formulaire, 2,675 t : $refusal")
        assertEquals(Res.string.validation_amount_two_decimals, refusal, "refusé dès le formulaire, comme le backend")

        val owner = device()
        owner.signedInAs("qa-c-decimals", "QA C Décimales")
        val (project, stage) = owner.newSite("QA C décimales")
        val purchase = owner.entryOf(owner.logs.createPurchaseEntry(stage, today().toString()), EntryType.PURCHASE)
        val sand = owner.materials.createMaterial(project, "Sable", "t")
        val line = owner.purchaseLines.createLine(purchase, CreatePurchaseLineInput(sand.localId, 2.67, 40.5, null))
        owner.sync.syncNow()

        val row = owner.db.purchaseLineDao().findByLocalId(line)
        println("C décimales — 2,67 t à 40,50 : ${row?.syncStatus}/${row?.lastSyncError}")
        val (app, server) = owner.compare("C décimales", project)

        assertEquals(SyncStatus.SYNCED, row?.syncStatus, "deux décimales : acceptées par le serveur")
        assertEquals(mapOf("Sable" to 2.67), server)
        assertEquals(server, app)
    }
}
