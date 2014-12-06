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
import org.xml.sax.HandlerBase;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class FlexmiResource extends ResourceImpl {
	
	public static final String OPTION_FUZZY_CONTAINMENT_MATCHING = "fuzzyContainmentMatching";
	public static final String OPTION_ORPHANS_AS_TOP_LEVEL = "orphansAsTopLevel";
	public static final String OPTION_FUZZY_MATCHING_THRESHOLD = "fuzzyMatchingThreshold";
	
	protected EObjectIdManager eObjectIdManager = new EObjectIdManager();
	protected EObjectTraceManager eObjectTraceManager = new EObjectTraceManager();
	protected List<UnresolvedReference> unresolvedReferences = new ArrayList<UnresolvedReference>();
	protected Stack<Object> stack = new Stack<Object>();
	protected Locator locator = null;
	protected List<String> scripts = new ArrayList<String>();
	protected HashMap<String, EClass> eClassCache = new HashMap<String, EClass>();
	protected HashMap<EClass, List<EClass>> allSubtypesCache = new HashMap<EClass, List<EClass>>();
	
	protected boolean fuzzyContainmentSlotMatching = true;
	protected boolean orphansAsTopLevel = false;
	protected int fuzzyMatchingThreshold = 2;
	
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
			ioException.printStackTrace();
			throw ioException;
		}
		catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException(ex);
		}
	}
	
	protected void processOption(String key, String value) {
		try {
			if (OPTION_FUZZY_CONTAINMENT_MATCHING.equalsIgnoreCase(key)) {
				fuzzyContainmentSlotMatching = Boolean.parseBoolean(value);
			}
			else if (OPTION_ORPHANS_AS_TOP_LEVEL.equalsIgnoreCase(key)) {
				orphansAsTopLevel = Boolean.parseBoolean(value);
			}
			else if (OPTION_FUZZY_MATCHING_THRESHOLD.equalsIgnoreCase(key)) {
				fuzzyMatchingThreshold = Integer.parseInt(value);
			}
			else throw new Exception("Unknown option");
		}
		catch (Exception ex) {
			addParseWarning("Could not process option " + key + ": " + ex.getMessage());
		}
	}
	
	public void doLoadImpl(InputStream inputStream, Map<?, ?> options) throws Exception {
		getContents().clear();
		unresolvedReferences.clear();
		stack.clear();
		scripts.clear();
		eClassCache.clear();
		allSubtypesCache.clear();
		eObjectIdManager = new EObjectIdManager();
		
		if (options != null) {
			for (Object key : options.keySet()) {
				processOption(key + "", options.get(key) + "");
			}
		}
		
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
				else processOption(key, value);
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
		int line = 0;
		if (locator != null) line = locator.getLineNumber();
		addParseWarning(message, line);
	}
	
	protected void addParseWarning(final String message, final int line) {
		getWarnings().add(new FlexmiDiagnostic(message, line, this));
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
							new EReferenceSlot(eReference, unresolvedReference.getEObject()).newValue(candidate);
						}
					}
					resolvedReferences.add(unresolvedReference);
				}
				else {
					resolveReference(unresolvedReference, resolvedReferences);
				}
			}
			else {
				resolveReference(unresolvedReference, resolvedReferences);
			}
		}
		
		unresolvedReferences.removeAll(resolvedReferences);
		for (UnresolvedReference reference : unresolvedReferences) {
			addParseWarning("Could not resolve target " + reference.getValue() + " for reference " + reference.getAttributeName() + " (" + reference.getEReference().getName() + ")", reference.getLine());
		}
		eObjectIdManager = new EObjectIdManager();
	}
	
	protected void resolveReference(UnresolvedReference unresolvedReference, List<UnresolvedReference> resolvedReferences) {
		List<EObject> candidates = eObjectIdManager.getEObjectsById(unresolvedReference.getValue());
		if (unresolvedReference.resolve(candidates)) {
			resolvedReferences.add(unresolvedReference);
		}
	}
		
	@SuppressWarnings("unchecked")
	protected void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
		
		//Remove prefixes
		//TODO: Add option to disable this
		if (name.indexOf(":") > -1) {
			name = name.substring(name.indexOf(":")+1);
		}
		
		EObject eObject = null;
		EClass eClass = null;
		
		// We're at the root or we treat orphan elements as top-level
		if (stack.isEmpty() || (stack.peek() == null && orphansAsTopLevel)) {
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
			Object peek = stack.peek();
			
			// We find an orphan elmeent but don't treat it as top-level
			if (peek == null) {
				if (peek == null) {
					stack.push(null);
					addParseWarning("Could not map element " + name + " to an EObject");
					return;
				}
			}
			// The parent is an already-established containment slot
			else if (peek instanceof EReferenceSlot) {
				EReferenceSlot containmentSlot = (EReferenceSlot) peek;
				eClass = (EClass) eNamedElementForName(name, getAllSubtypes(containmentSlot.getEReference().getEReferenceType()));
				
				if (eClass != null) {
					eObject = eClass.getEPackage().getEFactoryInstance().create(eClass);
					containmentSlot.newValue(eObject);
					stack.push(eObject);
					setAttributes(eObject, attributes);
				}
				else {
					stack.push(null);
					addParseWarning("Could not map element " + name + " to an EObject");
				}
			}
			// The parent is an EObject
			else if (peek instanceof EObject) {
				EObject parent = (EObject) peek;
				
				EReference containment = null;
				
				// No attributes -> Check whether there is a containment reference with that name
				if (attributes.getLength() == 0) {
					if (fuzzyContainmentSlotMatching) {
						containment = (EReference) eNamedElementForName(name, parent.eClass().getEAllContainments());
					}
					else {
						containment = (EReference) eNamedElementForName(name, parent.eClass().getEAllContainments(), false);				
					}
					if (containment != null) {
						EReferenceSlot containmentSlot = new EReferenceSlot(containment, parent);
						stack.push(containmentSlot);
						return;
					}
				}
				
				// No containment references found
				// Find potential types for the element
				Set<EClass> candidates = new HashSet<EClass>();
				for (EReference eReference : parent.eClass().getEAllContainments()) {
					candidates.addAll(getAllSubtypes(eReference.getEReferenceType()));				
				}
				
				// Get the best match and an appropriate containment reference
				eClass = (EClass) eNamedElementForName(name, candidates);
				if (eClass != null) {
					for (EReference eReference : parent.eClass().getEAllContainments()) {
						if (getAllSubtypes(eReference.getEReferenceType()).contains(eClass)) {
							containment = eReference;
							break;
						}
					}
				}
				
				// Found an appropriate containment reference
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
				// No luck - add warning
				else {
					stack.push(null);
					addParseWarning("Could not map element " + name + " to an EObject");
				}
			}
		}
			
		
	}
	
	protected void endElement(String uri, String localName, String name) throws SAXException {
		Object object = stack.pop();
		if (object != null && object instanceof EObject) {
			EObject eObject = (EObject) object;
			eObjectTraceManager.trace(eObject, locator.getLineNumber());
		}
	}
	
	protected void setAttributes(EObject eObject, Attributes attributes) {
		
		List<EStructuralFeature> eStructuralFeatures = getCandidateStructuralFeaturesForAttribute(eObject.eClass());
		eObjectTraceManager.trace(eObject, locator.getLineNumber());
		
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
				eObjectIdManager.setEObjectId(eObject, value);
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
			if (!eClass.isAbstract()) allSubtypes.add(eClass);
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
	
	public EObjectTraceManager getEObjectTraceManager() {
		return eObjectTraceManager;
	}
	
	protected ENamedElement eNamedElementForName(String name, Collection<? extends ENamedElement> candidates, boolean fuzzy) {
		
		if (fuzzy) {
			int maxLongestSubstring = fuzzyMatchingThreshold;
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
		
		if (second.startsWith(first)) maxLen = maxLen * 2;
		
		return maxLen;
	}
}
