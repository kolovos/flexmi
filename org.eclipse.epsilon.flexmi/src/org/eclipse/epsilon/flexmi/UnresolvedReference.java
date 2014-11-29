package org.eclipse.epsilon.flexmi;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

public class UnresolvedReference {
	
	protected EObject eObject;
	protected EReference eReference;
	protected String value;
	
	public EObject getEObject() {
		return eObject;
	}
	
	public void setEObject(EObject eObject) {
		this.eObject = eObject;
	}
	
	public EReference getEReference() {
		return eReference;
	}
	
	public void seteReference(EReference eReference) {
		this.eReference = eReference;
	}
	
	public String getValue() {
		return value;
	}
	
	public void setValue(String value) {
		this.value = value;
	}
	
}
