package org.eclipse.epsilon.flexmi;

import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;

public class ContainmentSlot {
	
	protected EReference eReference;
	protected EObject container;
	
	public ContainmentSlot(EReference eReference, EObject container) {
		super();
		this.eReference = eReference;
		this.container = container;
	}
	
	public EReference getEReference() {
		return eReference;
	}
	
	public void setEReference(EReference eReference) {
		this.eReference = eReference;
	}
	
	public EObject getContainer() {
		return container;
	}
	
	public void setContainer(EObject container) {
		this.container = container;
	}
	
	@SuppressWarnings("unchecked")
	public void newValue(EObject eObject) {
		if (eReference.isMany()) ((List<Object>) container.eGet(eReference)).add(eObject);
		else container.eSet(eReference, eObject);
	}
	
}
