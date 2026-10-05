package com.tripwire.app.evidence

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.tripwire.core.evidence.EvidencePack
import com.tripwire.core.explain.ExplanationBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders an [EvidencePack] to PDF with Android's built-in writer, fully offline (EVD-01, EVD-04).
 * Sections follow Appendix E, in the order the national portal asks for them (EVD-08).
 */
class PackPdf(private val context: Context, private val explain: ExplanationBuilder) {
    private val pageW = 595 // A4 at 72 dpi
    private val pageH = 842
    private val margin = 40f
    private val width = (pageW - 2 * margin).toInt()

    private val title = TextPaint().apply { isAntiAlias = true; textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
    private val heading = TextPaint().apply { isAntiAlias = true; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(11, 110, 105) }
    private val body = TextPaint().apply { isAntiAlias = true; textSize = 10.5f; color = Color.BLACK }
    private val small = TextPaint().apply { isAntiAlias = true; textSize = 9f; color = Color.DKGRAY }
    private val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH)

    fun render(pack: EvidencePack, lang: String, appLabel: (String) -> String): File {
        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            y = margin
        }

        fun draw(text: String, paint: TextPaint, gapAfter: Float = 4f) {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(1.5f, 1f).build()
            if (y + layout.height > pageH - margin) newPage()
            val c = page!!.canvas
            c.save()
            c.translate(margin, y)
            layout.draw(c)
            c.restore()
            y += layout.height + gapAfter
        }

        fun section(name: String) {
            y += 8f
            draw(name, heading, 6f)
        }

        fun field(label: String, value: String?) {
            if (value.isNullOrBlank()) return
            draw("$label: $value", body)
        }

        newPage()
        draw("Cyber-fraud complaint pack", title, 2f)
        draw("For the 1930 helpline and cybercrime.gov.in. Generated ${fmt.format(Date(pack.createdAt))}.", small, 10f)

        section("1. Complainant")
        field("Name", pack.complainant.name)
        field("Phone", pack.complainant.phone)
        field("Email", pack.complainant.email)
        field("Address", pack.complainant.address)

        section("2. Incident summary")
        field("Type of fraud", pack.scamType)
        field("Stage reached", pack.stage)
        field("First contact", pack.firstContact?.let { fmt.format(Date(it)) })
        field("Total amount", pack.totalAmount?.format())
        pack.goldenHourEndsAt?.let { field("First hour ends", fmt.format(Date(it))) }

        section("3. Suspect identifiers")
        field("Phone numbers", pack.phoneNumbers.joinToString(", "))
        field("Usernames / names used", pack.usernames.joinToString(", "))
        field("Groups", pack.groupNames.joinToString(", "))
        field("UPI addresses", pack.upiHandles.joinToString(", "))
        field("Links", pack.links.joinToString(", "))

        if (pack.transactions.isNotEmpty()) {
            section("4. Transactions")
            pack.transactions.forEachIndexed { i, t ->
                draw(
                    listOfNotNull(
                        "#${i + 1}",
                        t.amount?.format(),
                        fmt.format(Date(t.time)),
                        t.utr?.let { "UTR $it" },
                        t.payeeHandle?.let { "to $it" },
                        t.payeeName,
                        t.bank,
                    ).joinToString("  ·  "),
                    body,
                )
            }
        }

        if (pack.apps.isNotEmpty()) {
            section("5. Apps installed")
            pack.apps.forEach { draw("${it.name} (${it.packageName}), installed from: ${it.installSource}", body) }
        }

        if (pack.failedChecks.isNotEmpty()) {
            section("6. Checks that failed")
            pack.failedChecks.forEach { draw("• $it", body) }
        }

        section("7. Timeline")
        pack.timeline.forEach { draw("${fmt.format(Date(it.time))}  [${it.app}]  ${it.description}", body, 3f) }

        if (pack.quotedMessages.isNotEmpty()) {
            section("8. Messages, quoted")
            pack.quotedMessages.forEach { m ->
                draw("${fmt.format(Date(m.time))}  ${appLabel(m.app)}  ${m.sender ?: ""}", small, 1f)
                draw("“${m.text}”", body, 6f)
            }
        }

        y += 10f
        draw(explain.string("pack.note", "en"), small)
        if (lang != "en") draw(explain.string("pack.note", lang), small)

        page?.let { doc.finishPage(it) }
        val dir = File(context.filesDir, "packs").apply { mkdirs() }
        val file = File(dir, "tripwire-complaint-${pack.createdAt}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** EVD-06: share through any app the user chooses. */
    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            null,
        )
    }
}
