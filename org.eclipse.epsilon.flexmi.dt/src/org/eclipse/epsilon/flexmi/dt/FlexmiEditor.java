package org.eclipse.epsilon.flexmi.dt;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.print.attribute.standard.Severity;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.MarkerUtilities;

public class FlexmiEditor extends TextEditor {

	private ColorManager colorManager;
	protected Job parseModuleJob = null;
	
	public FlexmiEditor() {
		super();
		colorManager = new ColorManager();
		setSourceViewerConfiguration(new XMLConfiguration(colorManager));
		setDocumentProvider(new XMLDocumentProvider());
	}
	
	@Override
	public void init(IEditorSite site, IEditorInput input)
			throws PartInitException {
		super.init(site, input);
		
		//outlinePage = createOutlinePage();
		
		
		final int delay = 1000;
		
		parseModuleJob = new Job("Parsing module") {
			
			protected int status = -1;
			
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				
				if (!isClosed()) {
					int textHashCode = getText().hashCode();
					if (status != textHashCode) {
						parseModule();
						status = textHashCode;
					}
					
					this.schedule(delay);
				}
				
				return Status.OK_STATUS;
			}
		};
		
		parseModuleJob.setSystem(true);
		parseModuleJob.schedule(delay);
	}
	
	public void parseModule() {
		// Return early if the file is opened in an unexpected editor (e.g. in a Subclipse RemoteFileEditor)
		if (!(getEditorInput() instanceof FileEditorInput)) return;
		
		FileEditorInput fileInputEditor = (FileEditorInput) getEditorInput();
		IFile file = fileInputEditor.getFile();
		
		final IDocument doc = this.getDocumentProvider().getDocument(
				this.getEditorInput());
		
		// Replace tabs with spaces to match
		// column numbers produced by the parser
		String code = doc.get();
		code = code.replaceAll("\t", " ");
		
		System.out.println(code);
		// TODO: Parse here
		
		final String markerType = "org.eclipse.epsilon.flexmi.dt.problemmarker";
		
		// Update problem markers
		// TODO: Update problem markers in all referenced files
		try {
			file.deleteMarkers(markerType, true, IResource.DEPTH_INFINITE);
			
			Map<String, Object> attr = new HashMap<String, Object>();
			attr.put(IMarker.LINE_NUMBER, new Integer(1));
			attr.put(IMarker.MESSAGE, "Horrible things happened");				
			int markerSeverity;
			markerSeverity = IMarker.SEVERITY_ERROR;
			attr.put(IMarker.SEVERITY, markerSeverity);
			MarkerUtilities.createMarker(file, attr, markerType);
			
		} catch (CoreException e1) {
			e1.printStackTrace();
		}
		
	}
	
	public boolean isClosed() {
		return this.getDocumentProvider() == null;
	}
	
	public String getText() {
		return this.getDocumentProvider().getDocument(
				this.getEditorInput()).get();
	}
	
	public void dispose() {
		colorManager.dispose();
		super.dispose();
	}

}
