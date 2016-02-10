package org.eclipse.epsilon.flexmi.dt;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.epsilon.flexmi.FlexmiResourceFactory;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

public class GenerateXMIAction implements IObjectActionDelegate {

	private Shell shell;
	protected IStructuredSelection selection;
	
	public GenerateXMIAction() {
		super();
	}
	
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		shell = targetPart.getSite().getShell();
	}

	/**
	 * @see IActionDelegate#run(IAction)
	 */
	public void run(IAction action) {
		try {
			IFile flexmiFile = (IFile) selection.getFirstElement();
			ResourceSet flexmiResourceSet = new ResourceSetImpl();
			flexmiResourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new FlexmiResourceFactory());
			Resource flexmiResource = flexmiResourceSet.createResource(URI.createPlatformResourceURI(flexmiFile.getFullPath().toOSString()));
			flexmiResource.load(null);
			ResourceSet xmiResourceSet = new ResourceSetImpl();
			xmiResourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl() {
				@Override
				public Resource createResource(URI uri) {
					return new XMIResourceImpl(uri) {
						@Override
						protected boolean useUUIDs() {
							return true;
						}
					};
				}
			});
			Resource xmiResource = xmiResourceSet.createResource(URI.createPlatformResourceURI(flexmiFile.getFullPath().toOSString().replace(".flexmi", ".xmi")));
			xmiResource.getContents().addAll(EcoreUtil.copyAll(flexmiResource.getContents()));
			xmiResource.save(null);
			flexmiFile.getParent().refreshLocal(1, new NullProgressMonitor());
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void selectionChanged(IAction action, ISelection selection) {
		this.selection = (IStructuredSelection) selection;
	}

}
