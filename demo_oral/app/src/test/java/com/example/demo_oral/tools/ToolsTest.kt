package com.example.demo_oral.tools

import com.example.demo_oral.llm.RoutedCall
import com.example.demo_oral.tools.resolve.Confirmation
import com.example.demo_oral.tools.resolve.TimeExpressions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimeExpressionsTest {
    @Test
    fun parsesTimes() {
        assertEquals(LocalTime.of(7, 30), TimeExpressions.parseTime("7:30"))
        assertEquals(LocalTime.of(19, 30), TimeExpressions.parseTime("19h30"))
        assertEquals(LocalTime.of(7, 0), TimeExpressions.parseTime("las 7"))
        assertEquals(LocalTime.of(19, 0), TimeExpressions.parseTime("7 pm"))
        assertEquals(LocalTime.of(7, 30), TimeExpressions.parseTime("las siete y media"))
        assertEquals(LocalTime.of(8, 15), TimeExpressions.parseTime("8 y cuarto"))
        assertEquals(LocalTime.of(0, 10), TimeExpressions.parseTime("12:10 am"))
        assertNull(TimeExpressions.parseTime("25:00"))
        assertNull(TimeExpressions.parseTime("cuando quieras"))
    }

    @Test
    fun parsesDays() {
        val wednesday = LocalDate.of(2026, 9, 30)
        assertEquals(wednesday, TimeExpressions.parseDay("hoy", wednesday))
        assertEquals(wednesday.plusDays(1), TimeExpressions.parseDay("mañana", wednesday))
        assertEquals(wednesday.plusDays(2), TimeExpressions.parseDay("pasado mañana", wednesday))
        assertEquals(LocalDate.of(2026, 10, 2), TimeExpressions.parseDay("el viernes", wednesday))
        // The same weekday is next week, not today
        assertEquals(LocalDate.of(2026, 10, 7), TimeExpressions.parseDay("el miércoles", wednesday))
        assertEquals(LocalDate.of(2026, 12, 25), TimeExpressions.parseDay("25/12", wednesday))
        assertEquals(LocalDate.of(2027, 1, 5), TimeExpressions.parseDay("5/1", wednesday))
        assertNull(TimeExpressions.parseDay("algún día", wednesday))
    }
}

class ConfirmationTest {
    @Test
    fun understandsAnswers() {
        assertEquals(Confirmation.Answer.YES, Confirmation.parse("Sí, envíalo"))
        assertEquals(Confirmation.Answer.YES, Confirmation.parse("vale"))
        assertEquals(Confirmation.Answer.NO, Confirmation.parse("No, cancela"))
        assertEquals(Confirmation.Answer.UNKNOWN, Confirmation.parse("qué tiempo hace"))
        assertEquals(Confirmation.Answer.UNKNOWN, Confirmation.parse("sí no sé"))
    }

    @Test
    fun understandsCancellations() {
        assertTrue(Confirmation.isCancel("Olvídalo"))
        assertTrue(Confirmation.isCancel("da igual"))
        assertTrue(Confirmation.isCancel("déjalo, ya no hace falta"))
        assertTrue(!Confirmation.isCancel("no, el viernes"))
    }
}

private class FakeTool(
    name: String,
    keywords: List<String>,
    risk: Risk = Risk.SAFE,
    params: List<ToolParam> = emptyList(),
    permissions: List<String> = emptyList(),
) : Tool {
    override val spec = ToolSpec(name, "test tool", params, risk, keywords, permissions)
    var executed: ToolArgs? = null

    override fun card(args: ToolArgs) = ToolCard(ToolIcon.ALARM, spec.name)

    override fun describe(args: ToolArgs) = "really?"

    override suspend fun execute(args: ToolArgs): ToolResult {
        executed = args
        return ToolResult.Success("done")
    }
}

class ToolRegistryTest {
    private val alarm = FakeTool("set_alarm", listOf("alarm", "despiert"), params = listOf(
        ToolParam("time", ParamType.STRING, "time"),
        ToolParam("snooze", ParamType.INTEGER, "minutes", required = false),
    ))
    private val torch = FakeTool("turn_on_flashlight", listOf("linterna"))
    private val registry = ToolRegistry(listOf(alarm, torch))

    @Test
    fun keepsOnlyToolsWhoseKeywordsAreSaid() {
        assertEquals(listOf(alarm), registry.candidates("Despiértame mañana a las 7"))
        assertEquals(listOf(alarm), registry.candidates("pon una alarma"))
        assertEquals(listOf(torch), registry.candidates("¡Enciende la LINTERNA!"))
        assertTrue(registry.candidates("cuéntame un chiste").isEmpty())
    }

    @Test
    fun validatesCallsAgainstTheDeclaredParameters() {
        assertTrue(registry.validate("set_alarm", mapOf("time" to "7:30")) is ToolRegistry.Validation.Valid)
        assertTrue(registry.validate("set_alarm", mapOf("time" to "7:30", "snooze" to 5.0)) is ToolRegistry.Validation.Valid)
        assertTrue(registry.validate("set_alarm", emptyMap()) is ToolRegistry.Validation.Missing)
        assertTrue(registry.validate("set_alarm", mapOf("time" to "  ")) is ToolRegistry.Validation.Missing)
        assertTrue(registry.validate("launch_rocket", emptyMap()) is ToolRegistry.Validation.Invalid)
    }
}

class ToolAgentTest {
    private fun agent(
        vararg tools: Tool,
        permissions: Set<String> = emptySet(),
        route: (String) -> RoutedCall?,
    ) = ToolAgent(
        registry = ToolRegistry(tools.toList()),
        route = { utterance, _ -> route(utterance) },
        hasPermission = { it in permissions },
    )

    @Test
    fun plainConversationNeverReachesTheRouter() = runBlocking {
        var routed = false
        val agent = agent(FakeTool("set_alarm", listOf("alarm"))) { routed = true; null }
        assertEquals(AgentOutcome.NotATool, agent.handle("hola, qué tal"))
        assertEquals(false, routed)
    }

    @Test
    fun safeToolRunsRightAway() = runBlocking {
        val tool = FakeTool("set_alarm", listOf("alarm"), params = listOf(ToolParam("time", ParamType.STRING, "t")))
        val agent = agent(tool) { RoutedCall("set_alarm", mapOf("time" to "7:30")) }
        val outcome = agent.handle("pon una alarma a las 7:30") as AgentOutcome.Done
        assertEquals("done", outcome.text)
        assertTrue(outcome.success)
        assertEquals("set_alarm", outcome.card.title)
        assertEquals("7:30", tool.executed?.string("time"))
    }

    @Test
    fun routerAnsweringWithAToolThatWasNotOfferedIsIgnored() = runBlocking {
        val agent = agent(FakeTool("set_alarm", listOf("alarm")), FakeTool("send_sms", listOf("sms"))) {
            RoutedCall("send_sms", emptyMap())
        }
        assertEquals(AgentOutcome.NotATool, agent.handle("pon una alarma"))
    }

    @Test
    fun invalidCallFallsBackToConversation() = runBlocking {
        val tool = FakeTool("set_alarm", listOf("alarm"), params = listOf(ToolParam("time", ParamType.STRING, "t")))
        val agent = agent(tool) { RoutedCall("set_alarm", emptyMap()) }
        assertEquals(AgentOutcome.NotATool, agent.handle("pon una alarma"))
        assertNull(tool.executed)
    }

    @Test
    fun riskyToolWaitsForConfirmation() = runBlocking {
        val tool = FakeTool("call_contact", listOf("llama"), risk = Risk.CONFIRM)
        val agent = agent(tool) { RoutedCall("call_contact", emptyMap()) }
        val outcome = agent.handle("llama a mamá")
        assertTrue(outcome is AgentOutcome.Confirm)
        assertNull(tool.executed)
        agent.execute((outcome as AgentOutcome.Confirm).action)
        assertTrue(tool.executed != null)
    }

    @Test
    fun missingPermissionIsRequestedBeforeAnythingElse() = runBlocking {
        val tool = FakeTool("call_contact", listOf("llama"), risk = Risk.CONFIRM, permissions = listOf("CALL", "CONTACTS"))
        val agent = agent(tool, permissions = setOf("CALL")) { RoutedCall("call_contact", emptyMap()) }
        val outcome = agent.handle("llama a mamá")
        assertEquals(listOf("CONTACTS"), (outcome as AgentOutcome.NeedsPermission).permissions)
    }

    @Test
    fun failedPreflightIsShownAsAFailedCardAndNothingRuns() = runBlocking {
        val tool = object : Tool by FakeTool("send_sms", listOf("sms"), risk = Risk.CONFIRM) {
            override suspend fun preflight(args: ToolArgs) = ToolResult.Failed("no contact")
        }
        val agent = agent(tool) { RoutedCall("send_sms", emptyMap()) }
        val outcome = agent.handle("manda un sms") as AgentOutcome.Done
        assertEquals("no contact", outcome.text)
        assertEquals(false, outcome.success)
        assertTrue(outcome.risky)
    }

    @Test
    fun confirmationCarriesTheCardToShow() = runBlocking {
        val tool = FakeTool("call_contact", listOf("llama"), risk = Risk.CONFIRM)
        val agent = agent(tool) { RoutedCall("call_contact", emptyMap()) }
        val outcome = agent.handle("llama a mamá") as AgentOutcome.Confirm
        assertEquals("really?", outcome.question)
        assertEquals("call_contact", outcome.card.title)
    }

    @Test
    fun aQuestionIsNotTakenAsTheMissingValue() = runBlocking {
        val title = ToolParam("title", ParamType.STRING, "title", ask = "¿Cómo se llama el evento?")
        val tool = FakeTool("create_calendar_event", listOf("evento"), params = listOf(title))
        val agent = agent(tool) { null }
        val action = PendingAction(tool, ToolArgs(emptyMap()))
        val request = "crea un evento"
        assertEquals(FillResult.NoProgress, agent.fill(action, listOf(title), request, "¿Qué tiempo hace mañana?"))
        assertEquals(FillResult.NoProgress, agent.fill(action, listOf(title), request, "cuánto es dos más dos"))
        val filled = agent.fill(action, listOf(title), request, "Dentista") as FillResult.Progress
        assertTrue(filled.outcome is AgentOutcome.Done)
        assertEquals("Dentista", tool.executed?.string("title"))
    }
}
