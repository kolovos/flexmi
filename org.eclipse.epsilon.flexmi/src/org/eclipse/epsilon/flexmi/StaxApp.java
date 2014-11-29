package org.eclipse.epsilon.flexmi;

import java.io.ByteArrayInputStream;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.XMLEvent;

public class StaxApp {
	
	public static void main(String[] args) throws Exception{
		
		XMLInputFactory f = XMLInputFactory.newInstance();
		
		XMLEventReader r = f.createXMLEventReader(new ByteArrayInputStream("<?xml version=\"1.0\"?><foo bar=\"zoo\"/>".getBytes()));
		System.out.println(r);
		while (r.hasNext()) {
			XMLEvent next = r.nextEvent();
			next.asStartElement().getAttributeByName(null);
			System.out.println(next.getClass());
		}
	}
	
}
