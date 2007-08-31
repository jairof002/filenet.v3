package com.google.enterprise.connector.file;

import java.util.HashSet;
import java.util.Iterator;

import com.google.enterprise.connector.spi.Property;
import com.google.enterprise.connector.spi.RepositoryException;
import com.google.enterprise.connector.spi.Value;

public class FileDocumentProperty implements Property {

	private String name;

	private Iterator iter;

	public FileDocumentProperty(String name) {
		this.name = name;
	}

	public FileDocumentProperty(String name, HashSet value) {
		// TODO Auto-generated constructor stub
		this.name = name;
		this.iter = value.iterator();
	}

	public Value nextValue() throws RepositoryException {
		// TODO Auto-generated method stub
		Value value = null;

		if (this.iter.hasNext()) {
			value = (Value) this.iter.next();
		}

		return value;
	}

	public String getName() throws RepositoryException {

		return name;
	}

}