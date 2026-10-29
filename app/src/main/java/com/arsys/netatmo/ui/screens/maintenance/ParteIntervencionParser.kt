package com.arsys.netatmo.ui.screens.maintenance

import android.content.Context
import android.net.Uri
import com.arsys.netatmo.domain.model.MaintenanceType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * Parser for the "PARTE DE INTERVENCIÓN" PDFs from the official Vaillant /
 * Saunier Duval service network (Red Ofisat Nordeste). The layout is always the
 * same, so fields are located by their fixed labels. Every field is optional:
 * whatever is not found is left blank so the user can review and complete the
 * form before saving. Nothing here throws on unexpected content.
 */
object ParteIntervencionParser {

    data class Result(
        val numeroParte: String = "",
        val visitDate: String = "",       // dd/MM/yyyy
        val visitTimeRange: String = "",  // HH:mm - HH:mm
        val empresaServicio: String = "",
        val modelo: String = "",
        val numSerie: String = "",
        val version: String = "",
        val combustible: String = "",
        val observaciones: String = "",
        val piezas: List<String> = emptyList(),
        val readings: Map<String, String> = emptyMap()
    ) {
        /** True when the PDF actually looks like the expected parte format. */
        val isRecognized: Boolean
            get() = numeroParte.isNotBlank() || visitDate.isNotBlank() || readings.size >= 3

        /** Prefills the add-maintenance sheet with everything detected. */
        fun toForm(): BoilerRevisionForm {
            val description = buildString {
                if (observaciones.isNotBlank()) append(observaciones)
                if (piezas.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Piezas: ").append(piezas.joinToString("; "))
                }
            }
            fun r(key: String) = readings[key] ?: ""
            return BoilerRevisionForm(
                type = MaintenanceType.ANNUAL_REVISION,
                description = description,
                techName = empresaServicio,
                certRef = numeroParte,
                visitDate = visitDate,
                visitTimeRange = visitTimeRange,
                modelo = modelo,
                numSerie = numSerie,
                version = version,
                combustible = combustible,
                rendimiento = r("rendimiento"),
                co = r("co"),
                coCorrected = r("coCorrected"),
                coAmbiente = r("coAmbiente"),
                co2 = r("co2"),
                o2 = r("o2"),
                o2Ref = r("o2Ref"),
                temperaturaHumos = r("temperaturaHumos"),
                temperaturaAmbiente = r("temperaturaAmbiente"),
                temperaturaACS = r("temperaturaACS"),
                temperaturaAguaFria = r("temperaturaAguaFria"),
                caudalAgua = r("caudalAgua"),
                potenciaACS = r("potenciaACS"),
                presionDinamica = r("presionDinamica"),
                presionEstatica = r("presionEstatica"),
                tiro = r("tiro"),
                lambda = r("lambda"),
                perdidasChimenea = r("perdidasChimenea")
            )
        }
    }

    /** Extracts the raw text of the PDF chosen by the user. Throws on unreadable files. */
    fun extractText(context: Context, uri: Uri): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo abrir el archivo")
        input.use { stream ->
            PDDocument.load(stream).use { doc ->
                return PDFTextStripper().getText(doc)
            }
        }
    }

    fun parse(text: String): Result {
        val readings = linkedMapOf<String, String>()
        fun read(key: String, pattern: String) {
            val v = first(text, pattern, ignoreCase = true)?.num()
            if (!v.isNullOrBlank()) readings[key] = v
        }

        read("rendimiento", """Rendimiento\s*\(\s*([\d.,]+)\s*%""")
        read("co", """\bCO\s*\(\s*([\d.,]+)\s*ppm""")
        read("coCorrected", """CO corregido\s*\(\s*([\d.,]+)\s*ppm""")
        read("coAmbiente", """CO ambiente\s*\(\s*([\d.,]+)\s*ppm""")
        read("co2", """\bCO2\s*\(\s*([\d.,]+)\s*%""")
        read("o2", """\bO2\s*\(\s*([\d.,]+)\s*%""")
        read("o2Ref", """O2 referencia\s*\(\s*([\d.,]+)\s*%""")
        read("temperaturaHumos", """Temperatura Humos\s*\(\s*([\d.,]+)""")
        read("temperaturaAmbiente", """Temperatura Ambiente\s*\(\s*([\d.,]+)""")
        read("temperaturaACS", """Temperatura ACS\s*\(\s*([\d.,]+)""")
        read("temperaturaAguaFria", """Temperatura agua fr[ií]a\s*\(\s*([\d.,]+)""")
        read("caudalAgua", """Caudal agua\s*\(\s*([\d.,]+)""")
        read("potenciaACS", """Potencia instant[aá]nea ACS\s*\(\s*([\d.,]+)""")
        read("presionDinamica", """Presi[oó]n din[aá]mica\s*\(\s*([\d.,]+)""")
        read("presionEstatica", """Presi[oó]n est[aá]tica\s*\(\s*([\d.,]+)""")
        read("tiro", """\bTiro\s*\(\s*([\d.,]+)""")
        read("lambda", """\bLambda\s*\(\s*([\d.,]+)""")
        read("perdidasChimenea", """P[eé]rdidas por chimenea\s*\(\s*([\d.,]+)""")

        // Nº de parte: distinctive "26/544169" shape (dd/ddddd) that dates cannot produce.
        val numeroParte = first(text, """\b(\d{2}/\d{5,6})\b""") ?: ""

        // Visit date + time range: "- Visita: 13/08/2026 (10:04 - 10:39)"
        val visit = Regex(
            """Visita\s*:?\s*(\d{2}/\d{2}/\d{4})\s*\((\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})\)""",
            RegexOption.IGNORE_CASE
        ).find(text)
        val visitDate = visit?.groupValues?.get(1) ?: ""
        val visitTimeRange = if (visit != null) "${visit.groupValues[2]} - ${visit.groupValues[3]}" else ""

        // Service company: first S.L.U. name after the "SERVICIO PRESTADO POR:" marker
        // (the header also contains "VAILLANT SAUNIER DUVAL S.L.U.", which must be skipped).
        val empresaServicio = first(
            text,
            """SERVICIO PRESTADO POR:[\s\S]{0,300}?([A-ZÁÉÍÓÚÑÜ][A-ZÁÉÍÓÚÑÜ0-9 ,.&-]{2,60}?S\.?\s?L\.?\s?U\.)"""
        )?.normalizeWs() ?: ""

        // Equipment model / serial: the equipment table values are NOT in parentheses;
        // the combustion analyzer also has "Modelo (330-2...)" and "Nº Serie (03095929)".
        val modelo = firstNonParenValue(text, "Modelo")
            ?.substringBefore(" Energía")?.trim() ?: ""
        val numSerie = firstNonParenValue(text, "N[ºo°]\\s*Serie")
            ?.substringBefore(" Inicio")?.trim() ?: ""

        // Analyzer version / fuel, inside parentheses in the readings block.
        val version = first(text, """Versi[oó]n\s*\(([^)]+)\)""", ignoreCase = true)?.trim() ?: ""
        val combustible = first(text, """Combustible\s*\(([^)]+)\)""", ignoreCase = true)?.trim() ?: ""

        // Technician observations: text between "Técnico:" and the visits section.
        val observaciones = first(
            text,
            """T[ée]cnico\s*:\s*([\s\S]{10,600}?)(?:DATOS DE LAS VISITAS|El Cliente solicita)""",
            ignoreCase = true
        )?.normalizeWs() ?: ""

        // Used parts: "(1.0) KIT DE MANTENIMIENTO I 0010036500" style lines that carry
        // an article code (6+ digits); deduplicated (the line repeats in the DETALLES table).
        val partLine = Regex("""(?m)^\s*\(\s*\d+(?:[.,]\d+)?\s*\)\s+(.+?)\s*$""")
        val piezas = partLine.findAll(text)
            .map { it.groupValues[1].replace(Regex("""\s+[\d.,]+\s*€\s*$"""), "").trim() }
            .filter { it.contains(Regex("""\b\d{6,}\b""")) }
            .distinct()
            .toList()

        return Result(
            numeroParte = numeroParte,
            visitDate = visitDate,
            visitTimeRange = visitTimeRange,
            empresaServicio = empresaServicio,
            modelo = modelo,
            numSerie = numSerie,
            version = version,
            combustible = combustible,
            observaciones = observaciones,
            piezas = piezas,
            readings = readings
        )
    }

    private fun first(text: String, pattern: String, ignoreCase: Boolean = false): String? {
        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        return Regex(pattern, options).find(text)?.groupValues?.getOrNull(1)
    }

    /**
     * Finds the value for "<label> <value>" where the value does not start with "(",
     * whether it sits on the same line ("Modelo   THEMA CONDENS ...") or on the next
     * non-empty line ("Modelo\n\nTHEMA CONDENS ..."), depending on the extractor layout.
     */
    private fun firstNonParenValue(text: String, label: String): String? {
        val sameLine = Regex("""(?m)$label[ \t]+([^\n]+)""", RegexOption.IGNORE_CASE)
        val nextLine = Regex("""$label[ \t]*\n(?:[ \t]*\n)*[ \t]*([^\n]+)""", RegexOption.IGNORE_CASE)
        return sequenceOf(sameLine, nextLine)
            .flatMap { re -> re.findAll(text).map { it.groupValues[1].trim() } }
            .firstOrNull { it.isNotBlank() && !it.startsWith("(") }
            ?.cutAtColumnBreak()
    }

    /** In column layouts the next column follows after 2+ spaces; keep only the first. */
    private fun String.cutAtColumnBreak(): String = split(Regex("""\s{2,}"""))[0]

    private fun String.normalizeWs(): String = replace(Regex("""\s+"""), " ").trim()

    private fun String.num(): String = replace(',', '.').trim()
}
