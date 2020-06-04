import com.intellij.database.model.DasColumn
import com.intellij.database.model.DasObject
import com.intellij.database.model.DasTable
import com.intellij.database.util.Case
import com.intellij.database.util.DasUtil
import com.intellij.psi.codeStyle.NameUtil

import javax.swing.*

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
        (~/(?i)char|varchar/)                : "string"
]

FILES.chooseDirectoryAndSave("Choose directory", "Choose where to store generated files") { dir ->
    SELECTION.filter { it instanceof DasTable }.each { generate(it, dir) }
}

void generate(DasObject table, String dir) {
    String className = fixName(table.getName(), true)
    String fileName = table.getName()
    List fields = calcFields(table)
    String string = generateString(className, fields)

    new File(dir, fileName + ".cs").write(string)
}

static String generateString(String className, List columnList) {
    StringBuffer stringBuffer = new StringBuffer()

    int listSize = columnList.size()

    stringBuffer.append("using System;")
    stringBuffer.append("\n")
    stringBuffer.append("using System.ComponentModel.DataAnnotations;")
    stringBuffer.append("\n")
    stringBuffer.append("")
    stringBuffer.append("\n")
    stringBuffer.append("namespace DbContext.Models")
    stringBuffer.append("\n")
    stringBuffer.append("{")
    stringBuffer.append("\n")
    stringBuffer.append("\tpublic class ${className}Model")
    stringBuffer.append("\n")
    stringBuffer.append("\t{")

    for (int i = 0; i < listSize; i++) {
        Object column = columnList.getAt(i)

        if (column.HasComment) {
            stringBuffer.append("\n")
            stringBuffer.append("\t\t/// <summary>")
            stringBuffer.append("\n")
            stringBuffer.append("\t\t/// ${convertEncoding(column.Comment)}")
            stringBuffer.append("\n")
            stringBuffer.append("\t\t/// </summary>")
            stringBuffer.append("\n")
        }
        if (column.IsPrimary) {
            stringBuffer.append("\t\t[Key]")
            stringBuffer.append("\n")
        }

        stringBuffer.append("[Column(\"${column.RawName}\", TypeName = \"${column.RawTypeString}\")]")
        stringBuffer.append("\n")

        stringBuffer.append("public ${column.TypeString}")
        stringBuffer.append("${if (!column.IsNotNull && column.TypeString != "string") "?" else ""}")
        stringBuffer.append(" ")
        stringBuffer.append("${fixName(column.Name, true)} { get; set; }")

        if (i < listSize - 1) {
            stringBuffer.append("\n")
        }
    }

    stringBuffer.append("\n")
    stringBuffer.append("\t}")
    stringBuffer.append("\n")
    stringBuffer.append("}")

    return stringBuffer.toString()
}

static String convertEncoding(String inputString) {
    byte[] strBuffer = inputString.getBytes("UTF-8")
    String resultValue = new String(strBuffer)
    return resultValue
}

List calcFields(DasObject table) {
    List list = DasUtil.getColumns(table).reduce([]) { fields, col ->
        String spec = Case.LOWER.apply(col.getDataType().getSpecification())
        Boolean isNotNull = col.isNotNull()
        def typeStr = typeMapping.find { p, t -> p.matcher(spec).find() }.value

        fields += new Object() {
            String Name = fixName(col.getName(), false)
            String RawName = col.getName()
            String TypeString = typeStr.toString()
            String RawTypeString = getColTypeAsString(col)
            Boolean IsPrimary = DasUtil.isPrimary(col)
            Boolean IsNotNull = isNotNull
            String Comment = col.getComment()
            Boolean HasComment = Comment != ""
        }
    }

    return list
}

static String getColTypeAsString(DasColumn column) {
    String colTypeString = column.getDataType().getSpecification().replace("unsigned", " ").trim()
    return colTypeString
}

static String fixName(String inputString, Boolean capitalize) {
    String tmpString = NameUtil.splitNameIntoWords(inputString)
            .collect { Case.LOWER.apply(it).capitalize() }
            .join("")
            .replaceAll(/[^\p{javaJavaIdentifierPart}[_]]/, "_")

    String result = capitalize || tmpString.length() == 1 ? tmpString : Case.LOWER.apply(tmpString[0]) + tmpString[1..-1]

    return result
}