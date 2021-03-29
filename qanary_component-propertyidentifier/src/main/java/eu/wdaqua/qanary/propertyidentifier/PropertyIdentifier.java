package eu.wdaqua.qanary.propertyidentifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import info.debatty.java.stringsimilarity.Levenshtein;
import info.debatty.java.stringsimilarity.NormalizedLevenshtein;

import org.apache.jena.base.Sys;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.commons.QanaryQuestion;
import eu.wdaqua.qanary.commons.QanaryUtils;
import eu.wdaqua.qanary.component.QanaryComponent;
import eu.wdaqua.qanary.exceptions.SparqlQueryFailed;


@Component
/**
 * This component connected automatically to the Qanary pipeline.
 * The Qanary pipeline endpoint defined in application.properties (spring.boot.admin.url)
 * @see <a href="https://github.com/WDAqua/Qanary/wiki/How-do-I-integrate-a-new-component-in-Qanary%3F" target="_top">Github wiki howto</a>
 */
public class PropertyIdentifier extends QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(PropertyIdentifier.class);

	public static List<String> getVerbsNouns(String documentText) {
		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos,lemma");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		List<String> postags = new ArrayList<>();
		String lemmetizedQuestion = "";
		// Create an empty Annotation just with the given text
		Annotation document = new Annotation(documentText);
		// run all Annotators on this text
		pipeline.annotate(document);
		// Iterate over all of the sentences found
		List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
		for (CoreMap sentence : sentences) {
			// Iterate over all tokens in a sentence
			for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
				// Retrieve and add the lemma for each word into the
				// list of lemmas
				String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
				if (pos.contains("VB") || pos.contains("IN") || pos.contains("NN") || pos.contains("JJ")) {
					postags.add(token.get(CoreAnnotations.LemmaAnnotation.class));
				}
			}
		}
		return postags;
	}

	public static boolean isAlphaNumeric(String s) {
		return s != null && s.matches("^[a-zA-Z0-9]*$");
	}
	/**
	 * implement this method encapsulating the functionality of your Qanary
	 * component, some helping notes w.r.t. the typical 3 steps of implementing a
	 * Qanary component are included in the method (you might remove all of them)
	 * 
	 * @throws SparqlQueryFailed
	 */
	public QanaryMessage process(QanaryMessage myQanaryMessage) throws Exception {
		logger.info("process: {}", myQanaryMessage);

		QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);
		QanaryQuestion<String> myQanaryQuestion = this.getQanaryQuestion(myQanaryMessage);
		String myQuestion = myQanaryQuestion.getTextualRepresentation();
		logger.info("Question: {}", myQuestion);
		// TODO: implement processing of question

		List<String> allVerbs = getVerbsNouns(myQuestion);
		List<String> relationList = new ArrayList<String>();
		List<Property> properties = new ArrayList<Property>();
		List<String> valuePropertyList = new ArrayList<String>();
		boolean valueFlag = false;
		Set<String> coonceptsUri = new HashSet<String>();
		ResultSet r;

		Set<Concept> concepts = new HashSet<Concept>();
		String sparql = "PREFIX qa: <http://www.wdaqua.eu/qa#> "
				+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> "
				+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> "//
				+ "SELECT ?start ?end ?uri " + "FROM <" + myQanaryQuestion.getInGraph() + "> " //
				+ "WHERE { " //
				+ "    ?a a qa:AnnotationOfConcepts . " + "?a oa:hasTarget [ "
				+ "		     a               oa:SpecificResource; " //
				+ "		     oa:hasSource    ?q; " //
				+ "	         oa:hasSelector  [ " //
				+ "			         a        oa:TextPositionSelector ; " //
				+ "			         oa:start ?start ; " //
				+ "			         oa:end   ?end " //
				+ "		     ] " //
				+ "    ] . " //
				+ " ?a oa:hasBody ?uri ; " + "    oa:annotatedBy ?annotator " //
				+ "} " + "ORDER BY ?start ";

		r = myQanaryUtils.selectFromTripleStore(sparql);
		while (r.hasNext()) {
			QuerySolution s = r.next();
			Concept conceptTemp = new Concept();
			conceptTemp.begin = s.getLiteral("start").getInt();
			conceptTemp.end = s.getLiteral("end").getInt();
			conceptTemp.link = s.getResource("uri").getURI();
			if (conceptTemp.link.contains("dbpedia.org")) {
				concepts.add(conceptTemp);
				coonceptsUri.add(conceptTemp.link);
				logger.info("Concept start {}, end {} concept {} link {}", conceptTemp.begin, conceptTemp.end,
						myQuestion.substring(conceptTemp.begin, conceptTemp.end), conceptTemp.link);
			}
		}

		System.out.println("all verbs: " + allVerbs);
		for (String concept : coonceptsUri) {

			String classLabel = concept.substring(concept.lastIndexOf("/") + 1);
			System.out.println("inside for processing of class : "+ classLabel);
			File file = new File(
					"qanary_component-propertyidentifier/src/main/resources/dbpediaproperties/" + classLabel + "_dbpedia_label_of_properties.txt");
			Map<String, String> labelPropertyD = new HashMap<String, String>();
			System.out.println("Filename : "+ file.getName());
			if (file.exists()) {
				System.out.println("opening file : " + file.getName());
				BufferedReader br = new BufferedReader(new FileReader(file));

				String line = "";
				while ((line = br.readLine()) != null) {
					String splitedLine[] = line.split(",");
					if (splitedLine.length > 1) {
						labelPropertyD.put(splitedLine[0], splitedLine[1]);
					}
				}
				br.close();
				System.out.println("size is : " + labelPropertyD.size());
//				NormalizedLevenshtein l = new NormalizedLevenshtein();
				if (labelPropertyD.size() > 0) {
					double score = 0.0;
					for (Map.Entry<String, String> enrty : labelPropertyD.entrySet()) {
						for (String verb : allVerbs) {
							if (verb.contains(classLabel))
								continue;
							Pattern p = Pattern.compile("\\b" + enrty.getKey() + "\\b", Pattern.CASE_INSENSITIVE);
							Matcher m = p.matcher(verb);
//							score = l.distance(enrty.getKey(),verb);
//							System.out.println("Keyword: "+verb+"=================================="+enrty.getKey() + " == "+score);
//							System.out.println("Similarity score : "+score);
							if (!verb.equalsIgnoreCase(concept)) {
								if (m.find() && !enrty.getKey().equalsIgnoreCase("crosses")
										&& !enrty.getKey().equalsIgnoreCase("runs")
										&& !enrty.getKey().equalsIgnoreCase("south") && !enrty.getKey().equalsIgnoreCase("image") && !enrty.getKey().equalsIgnoreCase("find")&& !enrty.getKey().equalsIgnoreCase("number") && enrty.getKey().length() > 2) {
									Property property = new Property();
									if (relationList.size() == 0 || !relationList.contains(enrty.getValue())) {
										relationList.add(enrty.getValue());
										valuePropertyList.add(enrty.getValue());
										property.begin = myQuestion.toLowerCase().indexOf(enrty.getKey().toLowerCase());
										property.end = myQuestion.toLowerCase().indexOf(enrty.getKey().toLowerCase())
												+ enrty.getKey().length();
										property.label = enrty.getKey();
										property.uri = enrty.getValue();
										properties.add(property);
									}
									System.out.println("For class : " + classLabel + "   Found Value: " + enrty.getKey()
											+ " :" + enrty.getValue());
								}
							}
						}
					}
				}

			}
			for (Property DBpediaProperty : properties) {
				sparql = "prefix qa: <http://www.wdaqua.eu/qa#> "
						+ "prefix oa: <http://www.w3.org/ns/openannotation/core/> "
						+ "prefix xsd: <http://www.w3.org/2001/XMLSchema#> "
						+ "prefix dbp: <http://dbpedia.org/property/> " + "INSERT { " + "GRAPH <"
						+ myQanaryQuestion.getOutGraph() + "> { " + "  ?a a qa:AnnotationOfRelation . "
						+ "  ?a oa:hasTarget [ " + "           a    oa:SpecificResource; "
						+ "           oa:hasSource    <" + myQanaryQuestion.getUri() + ">; "
						+ "			  oa:hasSelector  [ " //
						+ "			         a        oa:TextPositionSelector ; " //
						+ "			         oa:start " + DBpediaProperty.begin + " ; " //
						+ "			         oa:end   " + DBpediaProperty.end + " " //
						+ "		     ] " //
						+ "  ] ; " + "     oa:hasValue <" + DBpediaProperty.uri + ">;"
						+ "	    oa:AnnotatedAt ?time  " + "}} " + "WHERE { " + "BIND (IRI(str(RAND())) AS ?a) ."
						+ "BIND (now() as ?time) " + "}";
				logger.info("Sparql query {}", sparql);
				myQanaryUtils.updateTripleStore(sparql, myQanaryMessage.getEndpoint().toString());
			}
		}

		return myQanaryMessage;
	}
	class Concept {
		public int begin;
		public int end;
		public String link;
	}

	public class Property {
		public int begin;
		public int end;
		public String label;
		public String uri;
	}
}
