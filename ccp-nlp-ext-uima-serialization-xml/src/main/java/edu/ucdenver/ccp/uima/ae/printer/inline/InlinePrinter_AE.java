package edu.ucdenver.ccp.uima.ae.printer.inline;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.SofaCapability;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.ConfigurationParameterFactory;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileWriterUtil;
import edu.ucdenver.ccp.common.file.FileWriterUtil.FileSuffixEnforcement;
import edu.ucdenver.ccp.common.file.FileWriterUtil.WriteMode;
import edu.ucdenver.ccp.common.reflection.ConstructorUtil;
import edu.ucdenver.ccp.nlp.ext.uima.shims.annotation.AnnotationDataExtractor;
import edu.ucdenver.ccp.nlp.ext.uima.shims.document.DocumentMetaDataExtractor;
import edu.ucdenver.ccp.uima.ae.printer.inline.InlineTag.InlinePostfixTag;
import edu.ucdenver.ccp.uima.ae.printer.inline.InlineTag.InlinePrefixTag;
import edu.ucdenver.ccp.uima.shims.annotation.Span;

/**
 * This JCas annotator is capable of outputting to file the document text from a specified view and
 * annotations from that view in an inline format, e.g.
 * "The <protein>ABC-1</protein> plays a role in..."
 * <p>
 * The content of the inline annotations is controlled by implementation(s) of the
 * {@link InlineTagExtractor} class.
 * 
 * @author bill
 * 
 */
@SofaCapability
public class InlinePrinter_AE extends JCasAnnotator_ImplBase {
	private static final Logger logger = Logger.getLogger(InlinePrinter_AE.class);

	/**
	 * Parameter name used in the UIMA descriptor file indicating the output file suffix
	 */
	public static final String PARAM_OUTPUT_FILE_SUFFIX = ConfigurationParameterFactory
			.createConfigurationParameterName(InlinePrinter_AE.class, "outputFileSuffix");

	/**
	 * the output file suffix
	 */
	@ConfigurationParameter(description = "name of the view to process", defaultValue = ".inline")
	private String outputFileSuffix;

	/**
	 * Parameter name used in the UIMA descriptor file indicating which view should be processed
	 */
	public static final String PARAM_VIEW_NAME_TO_PROCESS = ConfigurationParameterFactory
			.createConfigurationParameterName(InlinePrinter_AE.class, "viewNameToProcess");

	/**
	 * the name of the view to process
	 */
	@ConfigurationParameter(description = "name of the view to process", defaultValue = CAS.NAME_DEFAULT_SOFA)
	private String viewNameToProcess;

	/**
	 * Parameter name used in the UIMA descriptor file for the output directory
	 */
	public static final String PARAM_OUTPUT_DIRECTORY = ConfigurationParameterFactory.createConfigurationParameterName(
			InlinePrinter_AE.class, "outputDirectory");

	/**
	 * the directory where the inlined-annotation files will be written
	 */
	@ConfigurationParameter(description = "the directory where the inlined-annotation files will be written", mandatory = true)
	private File outputDirectory;

	/**
	 * Parameter name used in the UIMA descriptor file for the document meta data extractor
	 * implementation to use
	 */
	public static final String PARAM_DOCUMENT_META_DATA_EXTRACTOR_CLASS = ConfigurationParameterFactory
			.createConfigurationParameterName(InlinePrinter_AE.class, "metaDataExtractorClassName");

	/**
	 * The name of the DocumentMetaDataExtractor implementation to use
	 */
	@ConfigurationParameter(description = "name of the DocumentMetaDataExtractor implementation to use", defaultValue = "edu.uchsc.ccp.uima.shim.ccp.CcpDocumentMetaDataExtractor")
	private String metaDataExtractorClassName;

	/**
	 * this {@link DocumentMetaDataExtractor} will be initialized based on the class name specified
	 * by the metaDataExtractor parameter
	 */
	private DocumentMetaDataExtractor metaDataExtractor;

	/**
	 * Parameter name used in the UIMA descriptor file for the inline annotation extractor
	 * implementation(s) to use
	 */
	public static final String PARAM_INLINE_ANNOTATION_EXTRACTOR_CLASSES = ConfigurationParameterFactory
			.createConfigurationParameterName(InlinePrinter_AE.class, "inlineAnnotationExtractorClassNames");

	/**
	 * all {@link InlineTagExtractor} implementations
	 */
	@ConfigurationParameter(description = "names of InlineAnnotationExtractor implementations to use", defaultValue = "edu.uchsc.ccp.uima.ae.util.printer.inline.DefaultInlineAnnotationExtractor")
	private String[] inlineAnnotationExtractorClassNames;

	/**
	 * The collection of {@link InlineTagExtractor} implementations that will be used to generate
	 * inline annotations
	 */
	private Collection<InlineTagExtractor> inlineTagExtractors;

	/**
	 * This initialize method extracts the configuration parameters then initializes a
	 * {@link DocumentMetaDataExtractor} and one or more {@link InlineTagExtractor} implementations.
	 * 
	 * @see org.uimafit.component.JCasAnnotator_ImplBase#initialize(org.apache.uima.UimaContext)
	 */
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
		initializeMetaDataExtractor();
		initializeInlineAnnotationExtractors();
	}

	/**
	 * Initializes the {@link InlineTagExtractor} implemnetations based on the class name(s)
	 * provided by the inlineAnnotationExtractorClassNames parameter
	 */
	private void initializeInlineAnnotationExtractors() {
		inlineTagExtractors = new ArrayList<InlineTagExtractor>();
		for (String className : inlineAnnotationExtractorClassNames)
			inlineTagExtractors.add((InlineTagExtractor) ConstructorUtil.invokeConstructor(className));
	}

	/**
	 * Initializes a {@link DocumentMetaDataExtractor} based on the class name provided by the
	 * metaDataExtractorClassName parameter
	 */
	private void initializeMetaDataExtractor() {
		metaDataExtractor = (DocumentMetaDataExtractor) ConstructorUtil.invokeConstructor(metaDataExtractorClassName);
	}

	/**
	 * This process method creates a new file for each JCas that is processed. The generated file
	 * contains the document text from the specified view including annotations in an inline format,
	 * e.g. "The <animal>cow</animal> jumped over the <celestial_body>moon</celestial_body>."
	 * 
	 * @see org.apache.uima.analysis_component.JCasAnnotator_ImplBase#process(org.apache.uima.jcas.JCas)
	 */
	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {
		BufferedWriter writer = null;
		BufferedWriter excludedAnnotationWriter = null;
		try {
			JCas viewToProcess = jCas.getView(viewNameToProcess);
			writer = initializeOutputFileWriter(viewToProcess);
			Map<Integer, Collection<InlineTag>> characterOffsetToTagMap = computeCharacterOffsetToTagMap(viewToProcess);
			outputAnnotationsInline(characterOffsetToTagMap, viewToProcess.getDocumentText(), writer, jCas);

			for (InlineTagExtractor tagExtractor : inlineTagExtractors) {
				Collection<String> excludedAnnotations = tagExtractor.getExcludedAnnotations();
				logger.info("GOT EXCLUDED ANNOTATIONS ("+excludedAnnotations.size()+"): " + excludedAnnotations.toString());
				if (!excludedAnnotations.isEmpty()) {
					if (excludedAnnotationWriter == null) {
						excludedAnnotationWriter = initializeExcludedAnnotationFileWriter(jCas);
					}
					writeExcludedAnnotations(metaDataExtractor.extractDocumentId(jCas), excludedAnnotations, excludedAnnotationWriter, tagExtractor.getAnnotationDataExtractor());
				}
			}
		} catch (IOException ioe) {
			throw new AnalysisEngineProcessException(ioe);
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		} finally {
			try {
				if (writer != null) {
					writer.close();
				}
				if (excludedAnnotationWriter != null) {
					excludedAnnotationWriter.close();
				}
			} catch (IOException ioe) {
				throw new AnalysisEngineProcessException(ioe);
			}
		}
	}

	/**
	 * @param excludedAnnotations
	 * @param excludedAnnotationWriter
	 * @throws IOException 
	 */
	private void writeExcludedAnnotations(String documentId, Collection<String> excludedAnnotations,
			BufferedWriter writer, AnnotationDataExtractor annotationDataExtractor) throws IOException {
		logger.info("ANNOTATION DATA EXTRACTOR IS NULL???: " + (annotationDataExtractor==null));
		logger.info("ANNOTATION DATA EXTRACTOR CLASS: " + (annotationDataExtractor.getClass().getName()));
		for (String excludedAnnot : excludedAnnotations) {
//			String annotationType = annotationDataExtractor.getAnnotationType(excludedAnnot);
//			List<Span> annotationSpans = annotationDataExtractor.getAnnotationSpans(excludedAnnot);
//			String coveredText = annotationDataExtractor.getCoveredText(excludedAnnot);
//			String outStr = documentId + "\t" + annotationType + "\t" + annotationSpans.toString() + "\t" + coveredText;
//			String outStr = documentId + "\t" +  "\t" + annotationSpans.toString() + "\t" + coveredText;
			writer.write(excludedAnnot);
			writer.newLine();
		}
		
	}

	/**
	 * Obtains {@link InlineTag} instances for each of the {@link InlineTagExtractor}
	 * implementations being used. The tags are put into a map according the the character offset
	 * where they should reside (when displayed inline with the text)
	 * 
	 * @param viewToProcess
	 * @return
	 */
	private Map<Integer, Collection<InlineTag>> computeCharacterOffsetToTagMap(JCas viewToProcess) {
		Map<Integer, Collection<InlineTag>> offsetToTagMap = CollectionsUtil.initHashMap();
		for (InlineTagExtractor extractor : inlineTagExtractors) {
			for (Iterator<InlineTag> tagIterator = extractor.getInlineTagIterator(viewToProcess); tagIterator.hasNext();) {
				InlineTag tag = tagIterator.next();
				if (tag instanceof InlinePrefixTag)
					CollectionsUtil.addToOne2ManyMap(tag.getAnnotationSpan().getSpanStart(), tag, offsetToTagMap);
				else if (tag instanceof InlinePostfixTag)
					CollectionsUtil.addToOne2ManyMap(tag.getAnnotationSpan().getSpanEnd(), tag, offsetToTagMap);
				else
					throw new IllegalStateException("Unknown type of InlineTag: " + tag.getClass().getName());
			}
		}
		// logger.info("offsetToTagMap: " + offsetToTagMap.toString());
		return offsetToTagMap;
	}

	/**
	 * Outputs the document text including the inline annotations to the output file
	 * 
	 * @param characterOffsetToTagMap
	 *            a {@link Map<Integer, Collection<InlineTag>>} containing mappings from character
	 *            offset position to tags that need to be printed at that particular offset
	 *            position.
	 * @param viewToProcess
	 *            the JCas view to use when extracting annotations and the document text
	 * @param writer
	 *            the writer to use when printing to the output file
	 * @param jCas
	 *            the {@link JCas} for the current document
	 * @throws IOException
	 *             if an error occurs while writing to the output file
	 */
	private void outputAnnotationsInline(Map<Integer, Collection<InlineTag>> characterOffsetToTagMap,
			String documentText, BufferedWriter writer, JCas jCas) throws IOException {
		insertOutputFileHeader(writer, metaDataExtractor.extractDocumentEncoding(jCas));
		char[] charArray = documentText.toCharArray();
		Character previousCharacter = null;
		for (int i = 0; i < charArray.length; i++) {
			printInlinePostfixTags(i, characterOffsetToTagMap, writer);
			printInlinePrefixTags(i, characterOffsetToTagMap, writer);
			char c = charArray[i];
			String cStr = Character.toString(c);
			/*
			 * If the previous character is a "high surrogate" then the current character is its
			 * low-surrogate pair. This pair of characters is treated as a single unit and for
			 * simplicity we will not process either character in the pair. I'm not sure if this is
			 * necessary as the high-surrogate and low-surrogates each have distinct ranges, but
			 * we'll leave it in just in case (high-surrogates range, (\uD800-\uDBFF), the second
			 * from the low-surrogates range (\uDC00-\uDFFF)).
			 */
			if (previousCharacter != null && !Character.isHighSurrogate(previousCharacter))
				cStr = processDocumentCharacter(c);
			writer.append(cStr);
			previousCharacter = c;
		}
		printInlinePostfixTags(charArray.length, characterOffsetToTagMap, writer);
		insertOutputFileFooter(writer, metaDataExtractor.extractDocumentEncoding(jCas));

	}

	/**
	 * This method intentionally does nothing. It is designed as a hook for a subclass of
	 * {@link InlinePrinter_AE} in case the subclass would like to process the document characters
	 * prior to outputting them to the inlined file. This is the case, for example, when outputting
	 * XML as certain characters must be converted to an XML-safe representation e.g. "&" -->
	 * "&amp;" Note that if a high-surrogate Unicode character is detected, then no conversion is
	 * applied for that character and its low-surrogate pair (the next character).
	 * 
	 * @param c
	 * @return
	 */
	protected String processDocumentCharacter(char c) {
		return Character.toString(c);
	}

	/**
	 * This method is intentionally left empty. It is designed as a hook for a subclass of
	 * {@link InlinePrinter_AE} in case the subclass would like to add a header to the output file
	 * (as is the case when generating inline XML).
	 * 
	 * @param writer
	 *            a {@link BufferedWriter} initialized to the output file
	 * @param documentEncoding
	 *            the {@link CharacterEncoding} associated with the current {@link JCas}
	 */
	protected void insertOutputFileHeader(@SuppressWarnings("unused") BufferedWriter writer,
			@SuppressWarnings("unused") String documentEncoding) {
		// to be overwritten by an extension of InlinePrinter that wants to add a header to the
		// output file
	}

	protected void insertOutputFileFooter(BufferedWriter writer, String documentEncoding) {
		// to be overwritten by an extension of InlinePrinter that wants to add a footer to the
		// output file
	}

	/**
	 * Prints the {@InlinePrefixTag} instances at the current character offset in
	 * a reproducible order
	 * 
	 * @param offset
	 *            the offset into the document text where the tags need to be printed
	 * @param characterOffsetToTagMap
	 *            a {@link Map<Integer, Collection<InlineTag>>} containing mappings from character
	 *            offset position to tags that need to be printed at that particular offset
	 *            position.
	 * @param writer
	 *            the writer to use when printing to output
	 * @throws IOException
	 *             if an error occurs while writing the tags to the output file
	 */
	private void printInlinePrefixTags(int offset, Map<Integer, Collection<InlineTag>> characterOffsetToTagMap,
			BufferedWriter writer) throws IOException {
		if (!characterOffsetToTagMap.containsKey(offset))
			return;
		List<InlinePrefixTag> prefixTags = isolateTagType(characterOffsetToTagMap.get(offset), InlinePrefixTag.class);
		Collections.sort(prefixTags, InlineTag.getInlinePrefixTagComparator());
		for (InlinePrefixTag prefixTag : prefixTags) {
			// System.out.println("Printing pre-fix tag: " + prefixTag.getTagContents());
			writer.append(prefixTag.getTagContents());
		}
	}

	/**
	 * Prints the {@InlinePostfixTag} instances at the current character offset
	 * in a reproducible order
	 * 
	 * @param offset
	 *            the offset into the document text where the tags need to be printed
	 * @param characterOffsetToTagMap
	 *            a {@link Map<Integer, Collection<InlineTag>>} containing mappings from character
	 *            offset position to tags that need to be printed at that particular offset
	 *            position.
	 * @param writer
	 *            the writer to use when printing to output
	 * @throws IOException
	 *             if an error occurs while writing the tags to the output file
	 */
	private void printInlinePostfixTags(int offset, Map<Integer, Collection<InlineTag>> characterOffsetToTagMap,
			BufferedWriter writer) throws IOException {
		if (!characterOffsetToTagMap.containsKey(offset))
			return;
		List<InlinePostfixTag> postfixTags = isolateTagType(characterOffsetToTagMap.get(offset), InlinePostfixTag.class);
		Collections.sort(postfixTags, InlineTag.getInlinePostfixTagComparator());
		for (InlinePostfixTag postfixTag : postfixTags) {
			// System.out.println("Printing post-fix tag: " + postfixTag.getTagContents());
			writer.append(postfixTag.getTagContents());
		}
	}

	/**
	 * Given the input collection of {@link InlineTag} instances, this method returns only those
	 * instances of the specified class.
	 * 
	 * @param <T>
	 *            the class type of interest (either InlinePrefixTag.class or InlinePostfixTag.class
	 *            in this case)
	 * @param a
	 *            collection containing both {@link InlinePrefixTag} and {@link InlinePostfixTag}
	 *            instances
	 * @param tagClassOfInterest
	 *            the class type of interest (either InlinePrefixTag.class or InlinePostfixTag.class
	 *            in this case)
	 * @return a {@link List} containing only the class type of interest
	 */
	private <T extends InlineTag> List<T> isolateTagType(Collection<InlineTag> tags, Class<T> tagClassOfInterest) {
		List<T> tagsOfInterest = new ArrayList<T>();
		for (InlineTag tag : tags)
			if (tagClassOfInterest.isInstance(tag))
				tagsOfInterest.add(tagClassOfInterest.cast(tag));
		return tagsOfInterest;
	}

	/**
	 * Initializes a new {@link BufferedWriter} to write to an output file for a particular
	 * {@link JCas}. The output file name is composed of the document identifier (extracted from the
	 * {@link JCas}) appended with ".inline".
	 * 
	 * @param jCas
	 *            used to extract the document identifier and character encoding
	 * @return an initialized {@link BufferedWriter} to the output file designated for the input
	 *         {@link JCas}
	 * @throws FileNotFoundException
	 *             if an error occurs while initializing the {@link BufferedWriter}
	 */
	private BufferedWriter initializeOutputFileWriter(JCas jCas) throws FileNotFoundException {
		String documentId = metaDataExtractor.extractDocumentId(jCas);
		String encodingStr = metaDataExtractor.extractDocumentEncoding(jCas);
		CharacterEncoding encoding = CharacterEncoding.getEncoding(encodingStr);
		File outputFile = new File(outputDirectory, documentId + outputFileSuffix);
		return FileWriterUtil.initBufferedWriter(outputFile, encoding, WriteMode.OVERWRITE, FileSuffixEnforcement.OFF);
	}

	/**
	 * Initializes a new {@link BufferedWriter} to write to an output file for a particular
	 * {@link JCas}. The output file name is composed of the document identifier (extracted from the
	 * {@link JCas}) appended with ".inline".
	 * 
	 * @param jCas
	 *            used to extract the document identifier and character encoding
	 * @return an initialized {@link BufferedWriter} to the output file designated for the input
	 *         {@link JCas}
	 * @throws FileNotFoundException
	 *             if an error occurs while initializing the {@link BufferedWriter}
	 */
	private BufferedWriter initializeExcludedAnnotationFileWriter(JCas jCas) throws FileNotFoundException {
		String documentId = metaDataExtractor.extractDocumentId(jCas);
		String encodingStr = metaDataExtractor.extractDocumentEncoding(jCas);
		CharacterEncoding encoding = CharacterEncoding.getEncoding(encodingStr);
		File outputFile = new File(outputDirectory, documentId + outputFileSuffix + ".excluded_annotations");
		return FileWriterUtil.initBufferedWriter(outputFile, encoding, WriteMode.OVERWRITE, FileSuffixEnforcement.OFF);
	}

	/**
	 * Returns an initialized {@link InlinePrinter_AE} in the form of a UIMA {@link AnalysisEngine}
	 * 
	 * @param tsd
	 *            the relevant {@link TypeSystemDescription}; will depend on the pipline being run
	 *            and the types being printed
	 * @param outputDirectory
	 *            a reference to the directory where inlined-annotation files will be stored when
	 *            they are created
	 * @param viewNameToProcess
	 *            the name of the view from which to extract the document text and annotations
	 * @param documentMetaDataExtractorClass
	 *            an implementation of {@link DocumentMetaDataExtractor} to use to extract document
	 *            identifier and encoding information from the {@JCas}
	 * @param annotationExtractorClasses
	 *            implementation(s) of {@link InlineTagExtractor} that will be used to generate the
	 *            annotations that will be inlined with the document text in the output
	 * @return an initialized {@link AnalysisEngine} that will print inlined annotations to files in
	 *         the specified output directory
	 * @throws ResourceInitializationException
	 *             if an error occurs during {@link AnalysisEngine} initialization
	 */
	public static AnalysisEngine createAnalysisEngine(TypeSystemDescription tsd, File outputDirectory,
			String viewNameToProcess, Class<? extends DocumentMetaDataExtractor> documentMetaDataExtractorClass,
			String outputFileSuffix, Class<? extends InlineTagExtractor>... annotationExtractorClasses)
			throws ResourceInitializationException {
		String[] annotationExtractorClassNames = new String[annotationExtractorClasses.length];
		int i = 0;
		for (Class<? extends InlineTagExtractor> extractorClass : annotationExtractorClasses)
			annotationExtractorClassNames[i++] = extractorClass.getName();

		return createAnalysisEngine(InlinePrinter_AE.class, tsd, outputDirectory, viewNameToProcess,
				documentMetaDataExtractorClass, annotationExtractorClassNames, outputFileSuffix);
	}

	/**
	 * Returns an initialized {@link InlinePrinter_AE} in the form of a UIMA {@link AnalysisEngine}.
	 * This method can be used if only a single {@link InlineTagExtractor} is needed. It prevents a
	 * compiler warning dealing with arrays of generics.
	 * 
	 * @param tsd
	 *            the relevant {@link TypeSystemDescription}; will depend on the pipline being run
	 *            and the types being printed
	 * @param outputDirectory
	 *            a reference to the directory where inlined-annotation files will be stored when
	 *            they are created
	 * @param viewNameToProcess
	 *            the name of the view from which to extract the document text and annotations
	 * @param documentMetaDataExtractorClass
	 *            an implementation of {@link DocumentMetaDataExtractor} to use to extract document
	 *            identifier and encoding information from the {@JCas}
	 * @param annotationExtractorClasses
	 *            implementation(s) of {@link InlineTagExtractor} that will be used to generate the
	 *            annotations that will be inlined with the document text in the output
	 * @return an initialized {@link AnalysisEngine} that will print inlined annotations to files in
	 *         the specified output directory
	 * @throws ResourceInitializationException
	 *             if an error occurs during {@link AnalysisEngine} initialization
	 */
	public static AnalysisEngine createAnalysisEngine(TypeSystemDescription tsd, File outputDirectory,
			String viewNameToProcess, Class<? extends DocumentMetaDataExtractor> documentMetaDataExtractorClass,
			Class<? extends InlineTagExtractor> annotationExtractorClass, String outputFileSuffix)
			throws ResourceInitializationException {
		String[] annotationExtractorClassNames = new String[] { annotationExtractorClass.getName() };
		return createAnalysisEngine(InlinePrinter_AE.class, tsd, outputDirectory, viewNameToProcess,
				documentMetaDataExtractorClass, annotationExtractorClassNames, outputFileSuffix);
	}

	/**
	 * Returns an initialized {@link InlinePrinter_AE} in the form of a UIMA {@link AnalysisEngine}
	 * 
	 * @param tsd
	 *            the relevant {@link TypeSystemDescription}; will depend on the pipline being run
	 *            and the types being printed
	 * @param outputDirectory
	 *            a reference to the directory where inlined-annotation files will be stored when
	 *            they are created
	 * @param viewNameToProcess
	 *            the name of the view from which to extract the document text and annotations
	 * @param documentMetaDataExtractorClass
	 *            an implementation of {@link DocumentMetaDataExtractor} to use to extract document
	 *            identifier and encoding information from the {@JCas}
	 * @param annotationExtractorClasseNames
	 *            class names of implemented {@link InlineTagExtractor}s that will be used to
	 *            generate the annotations that will be inlined with the document text in the output
	 * @return an initialized {@link AnalysisEngine} that will print inlined annotations to files in
	 *         the specified output directory
	 * @throws ResourceInitializationException
	 */
	protected static AnalysisEngine createAnalysisEngine(Class<? extends InlinePrinter_AE> inlinePrinterClass,
			TypeSystemDescription tsd, File outputDirectory, String viewNameToProcess,
			Class<? extends DocumentMetaDataExtractor> documentMetaDataExtractorClass,
			String[] annotationExtractorClassNames, String outputFileSuffix) throws ResourceInitializationException {
		return AnalysisEngineFactory.createPrimitive(inlinePrinterClass, tsd, InlinePrinter_AE.PARAM_OUTPUT_DIRECTORY,
				outputDirectory.getAbsolutePath(), InlinePrinter_AE.PARAM_VIEW_NAME_TO_PROCESS, viewNameToProcess,
				InlinePrinter_AE.PARAM_DOCUMENT_META_DATA_EXTRACTOR_CLASS, documentMetaDataExtractorClass.getName(),
				InlinePrinter_AE.PARAM_INLINE_ANNOTATION_EXTRACTOR_CLASSES, annotationExtractorClassNames,
				InlinePrinter_AE.PARAM_OUTPUT_FILE_SUFFIX, outputFileSuffix);
	}

}
