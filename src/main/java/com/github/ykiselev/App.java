package com.github.ykiselev;

import com.google.common.collect.ImmutableMap;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * @author Yuriy Kiselev (uze@yandex.ru)
 * @since 21.03.2019
 */
public class App {

    public static void main(String[] args) throws Exception {
        new App().run();
    }

    private void run() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/test.xml")) {
            final XMLInputFactory factory = XMLInputFactory.newFactory();
            final Parser parser = new Parser(factory.createXMLStreamReader(is, "utf-8"));
            final NodeProcessor r = new NodeProcessor(ImmutableMap.of(
                    new QName("b"), new NodeProcessor(ImmutableMap.of(
                            new QName("d"), new NodeProcessor(ImmutableMap.of(
                                    new QName("b"), new NodeProcessor(new DefaultCharacters(System.out::println))
                            ))
                    )),
                    new QName("c"), new NodeProcessor(Collections.emptyMap())
            ));
            r.process(parser);
        }
    }


}

final class Parser {

    private final XMLStreamReader reader;

    private int depth;

    int depth() {
        return depth;
    }

    Parser(XMLStreamReader reader) {
        this.reader = reader;
    }

    boolean hasNext() throws XMLStreamException {
        return reader.hasNext();
    }

    int next() throws XMLStreamException {
        final int result = reader.next();
        if (result == XMLStreamReader.START_ELEMENT) {
            depth++;
        } else if (result == XMLStreamReader.END_ELEMENT) {
            depth--;
        }
        return result;
    }

    QName name() {
        return reader.getName();
    }

    boolean isStartElement() {
        return reader.isStartElement();
    }

    boolean isEndElement() {
        return reader.isEndElement();
    }

    boolean isCharacters() {
        return reader.isCharacters();
    }

    String characters() {
        return reader.getText();
    }

}

interface Characters {

    void append(String value);

    void consume();

}

final class NoOpCharacters implements Characters {

    static final Characters INSTANCE = new NoOpCharacters();

    @Override
    public void append(String value) {
    }

    @Override
    public void consume() {
    }
}

final class DefaultCharacters implements Characters {

    private StringBuilder sb;

    private final Consumer<String> consumer;

    DefaultCharacters(Consumer<String> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void append(String value) {
        if (sb == null) {
            sb = new StringBuilder();
        }
        sb.append(value);
    }

    @Override
    public void consume() {
        if (sb != null && sb.length() > 0) {
            consumer.accept(sb.toString());
            sb.setLength(0);
        }
    }
}

final class NodeProcessor {

    private final Map<QName, NodeProcessor> children;

    private final Characters characters;

    NodeProcessor(Map<QName, NodeProcessor> children, Characters characters) {
        this.children = requireNonNull(children);
        this.characters = requireNonNull(characters);
    }

    NodeProcessor(Map<QName, NodeProcessor> children) {
        this(children, NoOpCharacters.INSTANCE);
    }

    NodeProcessor(Characters characters) {
        this(Collections.emptyMap(), characters);
    }

    /**
     * Should read exactly one element's subtree.
     */
    void process(Parser parser) throws XMLStreamException {
        while (parser.hasNext() && !parser.isStartElement()) {
            parser.next();
        }
        if (!parser.isStartElement()) {
            return;
        }
        final QName name = parser.name();
        final int depth = parser.depth();
        System.out.println("starting " + parser.name() + "/" + (parser.depth() - 1));
        while (parser.hasNext()) {
            final int code = parser.next();
            if (code == XMLStreamReader.START_ELEMENT) {
                if (parser.depth() == depth + 1) {
                    final NodeProcessor reader = children.get(parser.name());
                    if (reader != null) {
                        reader.process(parser);
                    }
                }
            } else if (code == XMLStreamReader.END_ELEMENT) {
                if (depth > parser.depth() && name.equals(parser.name())) {
                    System.out.println("ending " + parser.name() + "/" + parser.depth());
                    characters.consume();
                    break;
                }
            } else if (code == XMLStreamReader.CHARACTERS) {
                characters.append(parser.characters());
            }
        }
    }
}