package com.example.demo_oral.tools.impl

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import com.example.demo_oral.R
import com.example.demo_oral.tools.ParamType
import com.example.demo_oral.tools.Risk
import com.example.demo_oral.tools.Tool
import com.example.demo_oral.tools.ToolArgs
import com.example.demo_oral.tools.ToolCard
import com.example.demo_oral.tools.ToolIcon
import com.example.demo_oral.tools.ToolParam
import com.example.demo_oral.tools.ToolResult
import com.example.demo_oral.tools.ToolSpec
import com.example.demo_oral.tools.resolve.ContactResolver

/** Sends an SMS. Always confirmed first: a misheard name must not send a message to the wrong person. */
class SendSmsTool(private val context: Context) : Tool {
    private val contacts = ContactResolver(context)

    override val spec = ToolSpec(
        name = "send_sms",
        description = "Sends a text message (SMS) to a contact.",
        params = listOf(
            ToolParam(
                "contact", ParamType.STRING, "Name of the contact, or a phone number",
                ask = context.getString(R.string.ask_sms_contact),
            ),
            ToolParam("message", ParamType.STRING, "Text of the message", ask = context.getString(R.string.ask_sms_message)),
        ),
        risk = Risk.CONFIRM,
        keywords = listOf("sms", "mensaje", "message", "texto", "text", "manda", "envia", "enviale", "escribe", "dile", "envoie"),
        permissions = listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS),
    )

    override suspend fun preflight(args: ToolArgs): ToolResult.Failed? =
        unknownContact(context, contacts, args.string("contact"))

    override fun card(args: ToolArgs) = ToolCard(
        icon = ToolIcon.SMS,
        title = context.getString(R.string.card_sms),
        fields = listOfNotNull(
            args.string("contact")?.let { context.getString(R.string.field_to) to contacts.displayName(it) },
            args.string("message")?.let { context.getString(R.string.field_message) to it },
        ),
    )

    override fun describe(args: ToolArgs): String {
        val contact = args.string("contact")?.let(contacts::find)
        return context.getString(R.string.tool_sms_confirm, contact?.name.orEmpty(), args.string("message").orEmpty())
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        val contact = args.string("contact")?.let(contacts::find)
            ?: return ToolResult.Failed(context.getString(R.string.tool_contact_not_found, args.string("contact").orEmpty()))
        val text = args.string("message") ?: return ToolResult.Failed(context.getString(R.string.tool_message_unclear))
        return try {
            val sms = context.getSystemService(SmsManager::class.java)
            sms.sendMultipartTextMessage(contact.number, null, sms.divideMessage(text), null, null)
            ToolResult.Success(context.getString(R.string.tool_sms_sent, contact.name))
        } catch (e: Exception) {
            Log.e("Tools", "SMS failed", e)
            ToolResult.Failed(context.getString(R.string.tool_sms_failed))
        }
    }
}

/** Starts a phone call. Confirmed first, like the SMS. */
class CallContactTool(private val context: Context) : Tool {
    private val contacts = ContactResolver(context)

    override val spec = ToolSpec(
        name = "call_contact",
        description = "Makes a phone call to a contact.",
        params = listOf(
            ToolParam(
                "contact", ParamType.STRING, "Name of the contact, or a phone number",
                ask = context.getString(R.string.ask_call_contact),
            ),
        ),
        risk = Risk.CONFIRM,
        keywords = listOf("llama", "llamar", "call", "appelle", "telefon", "phone", "ring"),
        permissions = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS),
    )

    override suspend fun preflight(args: ToolArgs): ToolResult.Failed? =
        unknownContact(context, contacts, args.string("contact"))

    override fun card(args: ToolArgs) = ToolCard(
        icon = ToolIcon.CALL,
        title = context.getString(R.string.card_call),
        fields = listOfNotNull(
            args.string("contact")?.let { context.getString(R.string.field_to) to contacts.displayName(it) },
        ),
    )

    override fun describe(args: ToolArgs): String {
        val contact = args.string("contact")?.let(contacts::find)
        return context.getString(R.string.tool_call_confirm, contact?.name.orEmpty())
    }

    override suspend fun execute(args: ToolArgs): ToolResult {
        val contact = args.string("contact")?.let(contacts::find)
            ?: return ToolResult.Failed(context.getString(R.string.tool_contact_not_found, args.string("contact").orEmpty()))
        val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", contact.number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (context.startSafely(intent)) {
            ToolResult.Success(context.getString(R.string.tool_call_started, contact.name))
        } else {
            ToolResult.Failed(context.getString(R.string.tool_call_failed))
        }
    }
}

private fun unknownContact(context: Context, contacts: ContactResolver, query: String?): ToolResult.Failed? =
    if (query != null && contacts.find(query) != null) null
    else ToolResult.Failed(context.getString(R.string.tool_contact_not_found, query.orEmpty()))
