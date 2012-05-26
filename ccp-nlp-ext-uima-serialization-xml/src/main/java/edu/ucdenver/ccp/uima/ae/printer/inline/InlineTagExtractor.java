package edu.ucdenver.ccp.uima.ae.printer.inline;

import java.util.Collection;
import java.util.Iterator;

import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import edu.ucdenver.ccp.nlp.ext.uima.shims.annotation.AnnotationDataExtractor;

/**
 * Interface for retrieving {@link InlineTag} instances from a {@link JCas}. Implementations of this
 * interface are used by the {@link InlinePrinter_AE} and its subclasses.
 * 
 * @author Center for Computational Pharmacology, UC Denver; ccpsupport@ucdenver.edu
 * 
 */
public interface InlineTagExtractor {

	/**
	 * This method returns an {@link Iterator<InlineTag>}. The {@link InlineTag} instances are
	 * created on an implementation-specific basis by examining annotations in the specified UIMA
	 * view.
	 * 
	 * @param view
	 *            the UIMA view to use when extracting the {@link InlineTag} instances
	 * @return an {@link Iterator<InlineTag>} based on annotations in the specified UIMA
	 *         {@link JCas} view
	 */
	public Iterator<InlineTag> getInlineTagIterator(JCas view);

	/**
	 * Why does this return String instead of Annotation? B/c I was seeing strange behavior when
	 * interrogating the Annotations that were returned. ClassMentions were null for some reason.
	 * 
	 * @return
	 */
	public Collection<String> getExcludedAnnotations();

	/**
	 * @return
	 */
	public AnnotationDataExtractor getAnnotationDataExtractor();

}
