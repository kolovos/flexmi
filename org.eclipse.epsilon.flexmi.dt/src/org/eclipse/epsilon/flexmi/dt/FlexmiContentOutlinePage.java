package org.eclipse.epsilon.flexmi.dt;

import org.eclipse.emf.ecore.provider.EcoreItemProviderAdapterFactory;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.edit.provider.ComposedAdapterFactory;
import org.eclipse.emf.edit.provider.ReflectiveItemProviderAdapterFactory;
import org.eclipse.emf.edit.provider.resource.ResourceItemProviderAdapterFactory;
import org.eclipse.emf.edit.ui.provider.AdapterFactoryContentProvider;
import org.eclipse.emf.edit.ui.provider.AdapterFactoryLabelProvider;
import org.eclipse.emf.edit.ui.provider.DecoratingColumLabelProvider;
import org.eclipse.emf.edit.ui.provider.DiagnosticDecorator;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

public class FlexmiContentOutlinePage extends ContentOutlinePage {
	
	protected ComposedAdapterFactory adapterFactory;
	
	public void setResourceSet(final ResourceSet resourceSet) {
		if (getSite() != null) {
			getSite().getShell().getDisplay().asyncExec(new Runnable() {
				
				public void run() {
					if (getTreeViewer() != null) {
						getTreeViewer().setInput(resourceSet);
					}
				}
			});
		}
		
	}
	
    @Override
    public void createControl(Composite parent)
    {
      super.createControl(parent);
      
      adapterFactory = new ComposedAdapterFactory(ComposedAdapterFactory.Descriptor.Registry.INSTANCE);

      adapterFactory.addAdapterFactory(new ResourceItemProviderAdapterFactory());
      adapterFactory.addAdapterFactory(new EcoreItemProviderAdapterFactory());
      adapterFactory.addAdapterFactory(new ReflectiveItemProviderAdapterFactory());

      TreeViewer contentOutlineViewer = getTreeViewer();
      contentOutlineViewer.addSelectionChangedListener(this);

      // Set up the tree viewer.
      contentOutlineViewer.setContentProvider(new AdapterFactoryContentProvider(adapterFactory));
      contentOutlineViewer.setLabelProvider(
    		  new DecoratingColumLabelProvider(
    				  new AdapterFactoryLabelProvider(adapterFactory), new DiagnosticDecorator(new ResourceSetImpl(), contentOutlineViewer)));
      contentOutlineViewer.setInput(new ResourceSetImpl());
      
    }
}