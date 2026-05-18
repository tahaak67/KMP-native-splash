package ly.com.tahaben.kmpnativesplash.util

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal fun parseXml(file: File, allowDoctype: Boolean = false): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        if (!allowDoctype) {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }
    val builder = factory.newDocumentBuilder().apply {
        // No-op resolver so the plist DTD isn't fetched over the network.
        setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
    }
    return builder.parse(file)
}

internal fun parseXmlString(content: String): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }
    return factory.newDocumentBuilder().parse(content.byteInputStream())
}

internal fun writeXml(
    document: Document,
    file: File,
    omitDeclaration: Boolean = false,
    plist: Boolean = false,
) {
    file.parentFile?.mkdirs()
    // Always strip whitespace text nodes the parser preserved from the original file. The
    // transformer re-indents from scratch — if we leave them in, we get the user-reported
    // "manifest looks mangled with random blank lines and stray indents" output.
    stripWhitespaceTextNodes(document)
    val tf = TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
        setOutputProperty("{http://xml.apache.org/xslt}indent-amount", if (plist) "1" else "4")
        setOutputProperty(OutputKeys.ENCODING, "utf-8")
        if (omitDeclaration) setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        if (plist) {
            setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "-//Apple//DTD PLIST 1.0//EN")
            setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "http://www.apple.com/DTDs/PropertyList-1.0.dtd")
        }
    }
    file.outputStream().use { tf.transform(DOMSource(document), StreamResult(it)) }
}

private fun stripWhitespaceTextNodes(node: Node) {
    val children = node.childNodes
    val toRemove = mutableListOf<Node>()
    for (i in 0 until children.length) {
        val child = children.item(i)
        when {
            child.nodeType == Node.TEXT_NODE && child.textContent.isBlank() -> toRemove.add(child)
            child.hasChildNodes() -> stripWhitespaceTextNodes(child)
        }
    }
    toRemove.forEach { node.removeChild(it) }
}

internal fun xmlToString(document: Document): String {
    val tf = TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
        setOutputProperty(OutputKeys.ENCODING, "utf-8")
    }
    val out = StringWriter()
    tf.transform(DOMSource(document), StreamResult(out))
    return out.toString()
}

internal fun Element.childElements(): List<Element> {
    val list = mutableListOf<Element>()
    val children = childNodes
    for (i in 0 until children.length) {
        val n = children.item(i)
        if (n.nodeType == Node.ELEMENT_NODE) list.add(n as Element)
    }
    return list
}

internal fun Element.findChild(tagName: String): Element? =
    childElements().firstOrNull { it.tagName == tagName }
