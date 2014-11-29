package org.eclipse.epsilon.flexmi;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.epsilon.emc.emf.InMemoryEmfModel;
import org.eclipse.epsilon.eol.EolModule;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class FlexmiResource extends ResourceImpl {
	
	protected HashMap<String, List<EObject>> idCache = new HashMap<String, List<EObject>>();
	protected List<UnresolvedReference> unresolvedReferences = new ArrayList<UnresolvedReference>();
	//protected List<ParseWarning> parseWarnings = new ArrayList<ParseWarning>();
	protected Stack<EObject> stack = new Stack<EObject>();
	protected Locator locator = null;
	protected List<String> scripts = new ArrayList<String>();
	protected HashMap<String, EClass> eClassCache = new HashMap<String, EClass>();
	protected HashMap<EClass, List<EClass>> allSubtypesCache = new HashMap<EClass, List<EClass>>();
	protected HashMap<EObject, Integer> eObjectLineTrace = new HashMap<EObject, Integer>();
	protected HashMap<Integer, EObject> lineEObjectTrace = new HashMap<Integer, EObject>();
	
	public static void main(String[] args) throws Exception {
		
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getPackageRegistry().put(EcorePackage.eINSTANCE.getNsURI(), EcorePackage.eINSTANCE);
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new FlexmiResourceFactory());
		Resource resource = resourceSet.createResource(URI.createURI(FlexmiResource.class.getResource("sample.xml").toString()));
		resource.load(null);
		
		EolModule module = new EolModule();
		module.parse("EReference.all.first().eType.name.println();");
		module.getContext().getModelRepository().addModel(new InMemoryEmfModel("M", resource));
		module.execute();
	}
	
	public FlexmiResource(URI uri) {
		super(uri);
	}
	
	@Override
	protected void doLoad(InputStream inputStream, Map<?, ?> options)
			throws IOException {
		try {
			doLoadImpl(inputStream, options);
		}
		catch (IOException ioException) {
			throw ioException;
		}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public void doLoadImpl(InputStream inputStream, Map<?, ?> options) throws Exception {
		getContents().clear();
		unresolvedReferences.clear();
		//parseWarnings.clear();
		stack.clear();
		scripts.clear();
		eClassCache.clear();
		allSubtypesCache.clear();
		lineEObjectTrace.clear();
		eObjectLineTrace.clear();
		
		SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
		DefaultHandler handler = new DefaultHandler() {
			
			@Override
			public void setDocumentLocator(Locator locator) {
				FlexmiResource.this.locator = locator;
			}
			
			@Override
			public void startDocument() throws SAXException {}
			
			@Override
			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
				FlexmiResource.this.startElement(uri, localName, qName, attributes);
			}
			
			@Override
			public void endElement(String uri, String localName, String name)
					throws SAXException {
				FlexmiResource.this.endElement(uri, localName, name);
			}
			
			@Override
			public void processingInstruction(String key, String value)
					throws SAXException {
				if ("nsuri".equalsIgnoreCase(key)) {
					EPackage ePackage = EPackage.Registry.INSTANCE.getEPackage(value);
					if (ePackage != null) getResourceSet().getPackageRegistry().put(ePackage.getNsURI(), ePackage);
					else addParseWarning("Failed to locate EPackage for nsURI " + value + " ");
				}
				else if ("eol".equalsIgnoreCase(key)) {
					scripts.add(value);
				}
			}
			
			@Override
			public void endDocument() throws SAXException {
				resolveReferences();
				for (String script : scripts) {
					EolModule module = new EolModule();
					try {
						module.parse(script);
						if (!module.getParseProblems().isEmpty()) {
							addParseWarning(module.getParseProblems().get(0).toString());
							return;
						}
						module.getContext().getModelRepository().addModel(new InMemoryEmfModel("M", FlexmiResource.this));
						module.execute();
					}
					catch (Exception ex) {}
				}
			}
		};
		saxParser.parse(inputStream, handler);
	}
	
	public List<UnresolvedReference> getUnresolvedReferences() {
		return unresolvedReferences;
	}
	
	protected void addParseWarning(final String message) {
		addParseWarning(message, locator.getLineNumber());
	}
	
	protected void addParseWarning(final String message, final int line) {
		getWarnings().add(new Diagnostic() {
			
			@Override
			public String getMessage() {
				return message;
			}
			
			@Override
			public String getLocation() {
				return FlexmiResource.this.getURI().toString();
			}
			
			@Override
			public int getLine() {
				return line;
			}
			
			@Override
			public int getColumn() {
				return 0;
			}
		});
	}
	
	@SuppressWarnings("unchecked")
	protected void resolveReferences() {
		
		List<UnresolvedReference> resolvedReferences = new ArrayList<UnresolvedReference>();
		
		for (UnresolvedReference unresolvedReference : unresolvedReferences) {
			EReference eReference = unresolvedReference.getEReference();
			if (eReference.isMany()) {
				
				if ("*".equals(unresolvedReference.getValue())) {
					Iterator<EObject> it = this.getAllContents();
					while (it.hasNext()) {
						EObject candidate = it.next();
						if (eReference.getEReferenceType().isInstance(candidate)) {
							((List<EObject>) unresolvedReference.getEObject().eGet(eReference)).add(candidate);
						}
					}
					resolvedReferences.add(unresolvedReference);
				}
				else {
					List<EObject> candidates = idCache.get(unresolvedReference.getValue());
					if (candidates != null) {
						for (EObject candidate : candidates) {
							if (eReference.getEReferenceType().isInstance(candidate)) {
								((List<EObject>) unresolvedReference.getEObject().eGet(eReference)).add(candidate);
								resolvedReferences.add(unresolvedReference);
								break;
							}
						}
					}
				}
			}
			else {
				List<EObject> candidates = idCache.get(unresolvedReference.getValue());
				if (candidates != null) {
					for (EObject candidate : candidates) {
						if (eReference.getEReferenceType().isInstance(candidate)) {
							unresolvedReference.getEObject().eSet(eReference, candidate);
							resolvedReferences.add(unresolvedReference);
							break;
						}
					}
				}
			}
		}
		
		unresolvedReferences.removeAll(resolvedReferences);
		for (UnresolvedReference reference : unresolvedReferences) {
			addParseWarning("Could not resolve target " + reference.getValue() + " for reference " + reference.getAttributeName() + " (" + reference.getEReference().getName() + ")", reference.getLine());
		}
		idCache.clear();
	}
	
	@SuppressWarnings("unchecked")
	protected void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
		EObject eObject = null;
		EClass eClass = null;
		
		if (stack.isEmpty()) {
			eClass = eClassForName(name);
			if (eClass != null) {
				eObject = eClass.getEPackage().getEFactoryInstance().create(eClass);
				getContents().add(eObject);
				setAttributes(eObject, attributes);
			}
			else {
				addParseWarning("Could not map element " + name + " to an EObject");
			}
			stack.push(eObject);
		}
		else {
			
			EObject parent = stack.peek();
			
			if (parent == null) {
				stack.push(null);
				addParseWarning("Could not map element " + name + " to an EObject");
				return;
			}
			
			EReference containment = null;
			
			Set<EClass> candidates = new HashSet<EClass>();
			for (EReference eReference : parent.eClass().getEAllContainments()) {
				candidates.addAll(getAllSubtypes(eReference.getEReferenceType()));				
			}
			
			eClass = (EClass) eNamedElementForName(name, candidates);
			if (eClass != null) {
				for (EReference eReference : parent.eClass().getEAllContainments()) {
					if (getAllSubtypes(eReference.getEReferenceType()).contains(eClass)) {
						containment = eReference;
						break;
					}
				}
			}
			
			if (containment != null) {
				eObject = eClass.getEPackage().getEFactoryInstance().create(eClass);
				if (containment.isMany()) {
					((List<EObject>) parent.eGet(containment)).add(eObject);
				}
				else {
					parent.eSet(containment, eObject);
				}
				setAttributes(eObject, attributes);
				stack.push(eObject);
			}
			else {
				stack.push(null);
				addParseWarning("Could not map element " + name + " to an EObject");
			}
		}
		
	}
	
	protected void endElement(String uri, String localName, String name) throws SAXException {
		EObject eObject = stack.pop();
		if (eObject != null) {
			eObjectLineTrace.put(eObject, locator.getLineNumber());
			lineEObjectTrace.put(locator.getLineNumber(), eObject);
		}
	}
	
	protected void setAttributes(EObject eObject, Attributes attributes) {
		
		List<EStructuralFeature> eStructuralFeatures = getCandidateStructuralFeaturesForAttribute(eObject.eClass());
		eObjectLineTrace.put(eObject, locator.getLineNumber());
		lineEObjectTrace.put(locator.getLineNumber(), eObject);
		
		for (int i=0;i<attributes.getLength();i++) {
			String name = attributes.getLocalName(i);
			String value = attributes.getValue(i);
			
			EStructuralFeature sf = (EStructuralFeature) eNamedElementForName(name, eStructuralFeatures);
			if (sf != null) {
				eStructuralFeatures.remove(sf);
				if (sf instanceof EAttribute) {
					setEAttributeValue(eObject, (EAttribute) sf, name, value);
				}
				else if (sf instanceof EReference) {
					EReference eReference = (EReference) sf;
					if (eReference.isMany()) {
						for (String valuePart : value.split(",")) {
							unresolvedReferences.add(new UnresolvedReference(eObject, eReference, name, valuePart.trim(), locator.getLineNumber()));
						}
					}
					else {
						unresolvedReferences.add(new UnresolvedReference(eObject, eReference, name, value, locator.getLineNumber()));
					}
				}
			}
			else {
				addParseWarning("Could not map attribute " + name + " to a structural feature of " + eObject.eClass().getName());
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	protected void setEAttributeValue(EObject eObject, EAttribute eAttribute, String attributeName, String value) {
		if (eAttribute.isMany()) {
			for (String valuePart : value.split(",")) {
				Object eValue = getEValue(eAttribute, attributeName, valuePart.trim());
				if (eValue == null) continue;
				((List<Object>) eObject.eGet(eAttribute)).add(eValue);
			}
		}
		else {
			Object eValue = getEValue(eAttribute, attributeName, value);
			if (eValue == null) return;
			eObject.eSet(eAttribute, eValue);
			if (eAttribute.isID() || "name".equalsIgnoreCase(eAttribute.getName())) {
				List<EObject> eObjects = idCache.get(value);
				if (eObjects == null) {
					eObjects = new ArrayList<EObject>();
					idCache.put(value, eObjects);
				}
				eObjects.add(eObject);
			}
		}
	}
	
	protected Object getEValue(EAttribute eAttribute, String attributeName, String value) {
		try {
			return eAttribute.getEAttributeType().getEPackage().getEFactoryInstance().createFromString(eAttribute.getEAttributeType(), value);
		}
		catch (Exception ex) {
			addParseWarning(ex.getMessage() + " in the value of " + attributeName);
			return null;
		}
	}
	
	protected List<EStructuralFeature> getCandidateStructuralFeaturesForAttribute(EClass eClass) {
		List<EStructuralFeature> eStructuralFeatures = new ArrayList<EStructuralFeature>();
		for (EStructuralFeature sf : eClass.getEAllStructuralFeatures()) {
			if (sf.isChangeable() && (sf instanceof EAttribute || ((sf instanceof EReference) && !((EReference) sf).isContainment()))) {
				eStructuralFeatures.add(sf);
			}
		}
		return eStructuralFeatures;
	}
	
	protected List<EClass> getAllConcreteEClasses() {
		List<EClass> eClasses = new ArrayList<EClass>();
		Iterator<Object> it = getResourceSet().getPackageRegistry().values().iterator();
		while (it.hasNext()) {
			EPackage ePackage = (EPackage) it.next();
			for (EClassifier eClassifier : ePackage.getEClassifiers()) {
				if (eClassifier instanceof EClass && !((EClass) eClassifier).isAbstract()) {
					eClasses.add((EClass) eClassifier);
				}
			}
		}
		return eClasses;
	}
	
	
	protected List<EClass> getAllSubtypes(EClass eClass) {
		List<EClass> allSubtypes = allSubtypesCache.get(eClass);
		if (allSubtypes == null) {
			allSubtypes = new ArrayList<EClass>();
			for (EClass candidate : getAllConcreteEClasses()) {
				if (candidate.getEAllSuperTypes().contains(eClass)) {
					allSubtypes.add(candidate);
				}
			}
			allSubtypes.add(eClass);
			allSubtypesCache.put(eClass, allSubtypes);
		}
		return allSubtypes;
	}
	
	protected EClass eClassForName(String name) {
		EClass eClass = eClassCache.get(name);
		if (eClass == null) {
			eClass = (EClass) eNamedElementForName(name, getAllConcreteEClasses());
			eClassCache.put(name, eClass);
		}
		return eClass;
		
	}
	
	protected ENamedElement eNamedElementForName(String name, Collection<? extends ENamedElement> candidates) {
		ENamedElement eNamedElement = eNamedElementForName(name, candidates, false);
		if (eNamedElement == null) eNamedElement = eNamedElementForName(name, candidates, true);
		return eNamedElement;
	}
	
	protected ENamedElement eNamedElementForName(String name, Collection<? extends ENamedElement> candidates, boolean fuzzy) {
		
		if (fuzzy) {
			System.out.println(name + candidates);
			int maxLongestSubstring = 2;
			ENamedElement bestMatch = null;
			for (ENamedElement candidate : candidates) {
				int longestSubstring = longestSubstring(candidate.getName().toLowerCase(), name.toLowerCase());
				if (longestSubstring > maxLongestSubstring) {
					maxLongestSubstring = longestSubstring;
					bestMatch = candidate;
				}
			}
			return bestMatch;			
		}
		else {
			for (ENamedElement candidate : candidates) {
				if (candidate.getName().equalsIgnoreCase(name)) return candidate;
			}
		}
		
		return null;
	}
	
	public HashMap<EObject, Integer> getEObjectLineTrace() {
		return eObjectLineTrace;
	}
	
	public HashMap<Integer, EObject> getLineEObjectTrace() {
		return lineEObjectTrace;
	}
	
	protected int longestSubstring(String first, String second) {
		if (first == null || second == null || first.length() == 0 || second.length() == 0) return 0;

		int maxLen = 0;
		int firstLength = first.length();
		int secondLength = second.length();
		int[][] table = new int[firstLength + 1][secondLength + 1];

		for (int f = 0; f <= firstLength; f++) table[f][0] = 0;
		for (int s = 0; s <= secondLength; s++) table[0][s] = 0;
		
		for (int i = 1; i <= firstLength; i++) {
			for (int j = 1; j <= secondLength; j++) {
				if (first.charAt(i - 1) == second.charAt(j - 1)) {
					if (i == 1 || j == 1) {
						table[i][j] = 1;
					} else {
						table[i][j] = table[i - 1][j - 1] + 1;
					}
					if (table[i][j] > maxLen) {
						maxLen = table[i][j];
					}
				}
			}
		}
		return maxLen;
	}
}
