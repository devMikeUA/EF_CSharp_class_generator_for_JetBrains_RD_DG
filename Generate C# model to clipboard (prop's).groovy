import com.intellij.database.model.DasObject
import com.intellij.database.model.DasTable
import com.intellij.database.util.Case
import com.intellij.database.util.DasUtil
import com.intellij.psi.codeStyle.NameUtil

import java.awt.*
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.util.List

/*
 * Available context bindings:
 *   SELECTION   Iterable<DasObject>
 *   PROJECT     project
 *   FILES       files helper
 */

typeMapping = [
        (~/(?i)tinyint/)                     : "bool",
        (~/(?i)int\(\d+\)\sunsigned/)        : "uint",
        (~/(?i)int/)                         : "int",
        (~/(?i)decimal/)                     : "decimal",
        (~/(?i)double|real/)                 : "double",
        (~/(?i)float/)                       : "float",
        (~/(?i)datetime|timestamp|date|time/): "DateTime",
        (~/(?i)char|varchar|text/)           : "string"
]

SELECTION.filter { it instanceof DasTable }.each { generate(it) }

void generate(DasObject table) {
    List fields = calcFields(table)
    String string = generateString(fields)

    setToClipboard(string)
}

void setToClipboard(String str) {
    StringSelection stringSelection = new StringSelection(str)
    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    clipboard.setContents(stringSelection, null)
}

String generateString(List columnList) {
    StringBuffer stringBuffer = new StringBuffer()

    int listSize = columnList.size()

    for (int i = 0; i < listSize; i++) {
        Object column = columnList.getAt(i)

        if (column.HasComment) {
            stringBuffer.append("/// <summary>")
            stringBuffer.append("\n")
            stringBuffer.append("/// ${column.Comment}")
            stringBuffer.append("\n")
            stringBuffer.append("/// </summary>")
            stringBuffer.append("\n")
        }
        if (column.IsPrimary) {
            stringBuffer.append("[Key]")
            stringBuffer.append("\n")
        }

        stringBuffer.append("public ${column.TypeString}")
        stringBuffer.append("${if (!column.IsNotNull && column.TypeString != "string") "?" else ""}")
        stringBuffer.append(" ")
        stringBuffer.append("${fixName(column.Name, true)} { get; set; }")

        if (i < listSize - 1) {
            stringBuffer.append("\n\n")
        }
    }

    return stringBuffer.toString()
}

List calcFields(DasObject table) {
    List list = DasUtil.getColumns(table).reduce([]) { fields, col ->
        String spec = Case.LOWER.apply(col.getDataType().getSpecification())
        Boolean isNotNull = col.isNotNull()
        def typeStringTmp = typeMapping.find { p, t -> p.matcher(spec).find() }?.value
        String typeString = typeStringTmp.toString().trim() == "null" ? "object" : typeStringTmp.toString().trim()

        fields += new Object() {
            String Name = fixName(col.getName(), false)
            String TypeString = typeString
            Boolean IsPrimary = DasUtil.isPrimary(col)
            Boolean IsNotNull = isNotNull
            String Comment = col.getComment()
            Boolean HasComment = (Comment != "" && Comment != null)
        }
    }

    return list
}

String fixName(String inputString, Boolean capitalize) {
    String tmpString = NameUtil.splitNameIntoWords(inputString)
            .collect { Case.LOWER.apply(it).capitalize() }
            .join("")
            .replaceAll(/[^\p{javaJavaIdentifierPart}[_]]/, "_")

    String result = capitalize || tmpString.length() == 1 ? tmpString : Case.LOWER.apply(tmpString[0]) + tmpString[1..-1]

    return result
}