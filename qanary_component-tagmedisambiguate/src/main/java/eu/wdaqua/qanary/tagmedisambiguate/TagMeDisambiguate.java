package eu.wdaqua.qanary.tagmedisambiguate;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.openrdf.query.resultio.stSPARQLQueryResultFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import eu.earthobservatory.org.StrabonEndpoint.client.EndpointResult;
import eu.earthobservatory.org.StrabonEndpoint.client.SPARQLEndpoint;
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
public class TagMeDisambiguate extends QanaryComponent {
	private final String tagMeKey = "150907b3-f257-4d8f-b22b-6d0e6c72f53d-843339462";
	private final String wikipediaLink = "https://en.wikipedia.org/wiki/";
	private final String yagoLink = "http://yago-knowledge.org/resource/";
	public final String yagoEndpoint = "http://pyravlos1.di.uoa.gr:8890/sparql";
	private static final Logger logger = LoggerFactory.getLogger(TagMeDisambiguate.class);

	public static String lemmatize(String documentText) {
		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, lemma");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		List<String> lemmas = new ArrayList<>();
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
				lemmas.add(token.get(CoreAnnotations.LemmaAnnotation.class));
				lemmetizedQuestion += token.get(CoreAnnotations.LemmaAnnotation.class) + " ";
			}
		}
		return lemmetizedQuestion;
	}

	public static int getNoOfLinks(String sparqlQuery, String endpointURI) {
		int count = 0;

		System.out.println("Sparql Query : "+sparqlQuery);
		Query query = QueryFactory.create(sparqlQuery);
//		System.out.println("sparql query :" + query.toString());
		QueryExecution exec = QueryExecutionFactory.sparqlService(endpointURI, query);
		ResultSet results = ResultSetFactory.copyResults(exec.execSelect());

		if (!results.hasNext()) {

		} else {
			while (results.hasNext()) {
				QuerySolution qs = results.next();
				count = qs.getLiteral("total").getInt();
//				System.out.println("total: " + count);
			}
		}

		return count;
	}
	/*public static void MyGETRequest(String query) throws IOException {

		URL urlForGetRequest = new URL("https://sparql.creodias.eu:20035/#/repositories/creodias/");
		String readLine = null;
		HttpURLConnection conection = (HttpURLConnection) urlForGetRequest.openConnection();
		conection.setRequestMethod("GET");
		conection.setRequestProperty("query", query); // set userId its a sample here
		conection.setRequestProperty("queryLn","SPARQL");
		conection.setRequestProperty("returnQueryMetadata","true");
		conection.setRequestProperty("infer","false");
		conection.setRequestProperty("uuid","basm3j32qrl6sgzfpdwb7g");
		int responseCode = conection.getResponseCode();
		System.out.println("============ Inside get request =============");

		if (responseCode == HttpURLConnection.HTTP_OK) {
			BufferedReader in = new BufferedReader(
					new InputStreamReader(conection.getInputStream()));
			StringBuffer response = new StringBuffer();
			while ((readLine = in .readLine()) != null) {
				response.append(readLine);
			} in .close();
			// print result
			System.out.println("Response from creodias endpoint : " + response.toString());
			//GetAndPost.POSTRequest(response.toString());
		} else {
			System.out.println("GET NOT WORKED");
		}

	}*/
	public static String runSparqlOnEndpoint(String sparqlQuery, String endpointURI) {

		Query query = QueryFactory.create(sparqlQuery);
		System.out.println("sparql query :" + query.toString());
		QueryExecution exec = QueryExecutionFactory.sparqlService(endpointURI, query);

		ResultSet results = ResultSetFactory.copyResults(exec.execSelect());

		if (!results.hasNext()) {
			System.out.println("There is no next!");
		} else {
			while (results.hasNext()) {

				QuerySolution qs = results.next();

				String uria = qs.get("x").toString();
				System.out.println("uria: " + uria);
				return uria;

			}
		}
		return null;
	}

	/**
	 * implement this method encapsulating the functionality of your Qanary
	 * component, some helping notes w.r.t. the typical 3 steps of implementing a
	 * Qanary component are included in the method (you might remove all of them)
	 * 
	 * @throws SparqlQueryFailed
	 */
	@Override
	public QanaryMessage process(QanaryMessage myQanaryMessage) throws Exception {

		try {
			long startTime = System.currentTimeMillis();
			logger.info("process: {}", myQanaryMessage);

			List<String> entitiesList = new ArrayList<String>();
			// STEP 1: Retrieve the information needed for the question

			// the class QanaryUtils provides some helpers for standard tasks
			QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);
			QanaryQuestion<String> myQanaryQuestion = this.getQanaryQuestion(myQanaryMessage);

			// checking creodias endpoint for sparql query
			//runSparqlOnEndpoint("select distinct * where { ?x ?p ?o } LIMIT 10 ","https://sparql.creodias.eu:20035/#/repositories/creodias/query");
//			MyGETRequest("select distinct * where { ?x ?p ?o } LIMIT 10");
			// Retrieves the question string

			String myQuestion = myQanaryQuestion.getTextualRepresentation();

			String countQuery1 = "SELECT (count(?p) as ?total) where { ";
			String countQuery2 = " ?p ?o. }";

			// Step 2: Call the TagMe service
			// Information about the service can be found here
			// https://services.d4science.org/web/tagme/wat-api
			String input = myQuestion;// lemmatize(myQuestion);

			logger.info("Input to TagMe: " + input);

			// http request to the TagMe service
			TagMeRequest tagMeRequest = new TagMeRequest(tagMeKey);

			TagMeResponse response = tagMeRequest.doRequest(input);

			ArrayList<NedAnnotation> annotations = response.getAnnotations();
			// Extract entities
			ArrayList<Link> links = new ArrayList<Link>();

			for (NedAnnotation ann : annotations) {
				if (ann.getTitle() != null) {
					Link l = new Link();
					l.link = this.yagoLink + ann.getTitle();
					l.linkCount = getNoOfLinks(countQuery1 + " <" + l.link + "> " + countQuery2,
							yagoEndpoint);
					l.begin = ann.getStart();
					l.end = ann.getEnd();
					String entlst = myQuestion.substring(l.begin,l.end);
					entitiesList.add(entlst); // adding identified entity to list for OSM

//					String yagoSparql = "select  ?x " +
//							"where { " +
//							"        {?x <http://www.w3.org/2002/07/owl#sameAs> <"+l.link+"> .} " +
//
//							"        UNION\n" +
//							"        { ?x <http://yago-knowledge.org/resource/hasWikipediaUrl> <"+l.link+"> . } " +
//							"        UNION\n" +
//							"        { ?x <http://www.w3.org/2002/07/owl#sameAs> <http://dbpedia.org/resource/"+l.link.substring(l.link.lastIndexOf('/')+1)+">  . } " +
//							"      } ";
//					System.out.println(yagoSparql);
//
//					String yagoLink = runSparqlOnEndpoint(yagoSparql, "https://linkeddata1.calcul.u-psud.fr/sparql");
//					l.link = yagoLink;
//					l.begin = ann.getStart();
//					l.end = ann.getEnd();
//					String removeThe = input.substring(l.begin, l.end);
//					String toLowerCase = removeThe.toLowerCase();
//					if(toLowerCase.startsWith("the ")) {
//						l.begin += 4;
//					}
					System.out.println("ADDING LINK for (" + input.substring(l.begin, l.end) + "):" + l.begin + " "
							+ l.end + " " + l.link);
					links.add(l);
				}
			}
			int cnt = 0;
			for (String instance : entitiesList) {
				if (StringUtils.isNumeric(instance))
					continue;
				String sparqlQuery = " select ?instance where { ?instance <http://www.app-lab.eu/osm/ontology#has_name> \""
						+ instance + "\"^^<http://www.w3.org/2001/XMLSchema#string> . }";// ?instance ?p ?o . }";

				String host = "pyravlos1.di.uoa.gr";
				Integer port = 8080;
				String appName = "geoqa/Query";
				String query = sparqlQuery;
				String format = "TSV";

				SPARQLEndpoint endpoint = new SPARQLEndpoint(host, port, appName);
				if (query.length() > 2) {
					try {

						EndpointResult result = endpoint.query(query,
								(stSPARQLQueryResultFormat) stSPARQLQueryResultFormat.valueOf(format));

						System.out.println("<----- Result ----->");

						String resultString[] = result.getResponse().replaceAll("\n", "\n\t").split("\n");
						for (int i = 1; i < resultString.length; i++) {

							Link l = new Link();
							l.link = resultString[i].trim();
							if(l.link.toLowerCase().contains("response")){
								System.out.println("check strabon endpoint : ");
								continue;
							}

							// l.begin = lemmatize(myQuestion).indexOf(instance);
							l.begin = myQuestion.indexOf(instance);
							// l.end = lemmatize(myQuestion).indexOf(instance)+instance.length();
							l.end = myQuestion.indexOf(instance) + instance.length();
							l.linkCount = getNoOfLinks(countQuery1 + " <" + l.link + "> " + countQuery2,
									"http://pyravlos1.di.uoa.gr:8080/geoqa/Query");
							links.add(l);
							System.out.println("Question: " + myQuestion + " ::: " + resultString[i].trim()
									+ "== index : " + l.begin + " : " + l.end+"\t"+i+"\t 1 resultStringLength: "+resultString.length);
						}

						System.out.println("<----- Result ----->");

					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				sparqlQuery = "select ?instance where { ?instance <http://www.app-lab.eu/gadm/ontology/hasName> \""
						+ instance + "\"@en . }";// ?instance ?p ?o . }";
				query = sparqlQuery;
				if (query.length() > 2) {
					try {

						EndpointResult result = endpoint.query(query,
								(stSPARQLQueryResultFormat) stSPARQLQueryResultFormat.valueOf(format));

						System.out.println("<----- Result ----->");
						// System.out.println(result.getResponse().replaceAll("\n", "\n\t"));
						String resultString[] = result.getResponse().replaceAll("\n", "\n\t").split("\n");

						for (int i = 1; i < resultString.length; i++) {
							Link l = new Link();
							l.link = resultString[i].trim();
							// l.begin = lemmatize(myQuestion).indexOf(instance);
							l.begin = myQuestion.indexOf(instance);
							// l.end = lemmatize(myQuestion).indexOf(instance)+instance.length();
							l.end = myQuestion.indexOf(instance) + instance.length();
							l.linkCount = getNoOfLinks(countQuery1 + " <" + l.link + "> " + countQuery2,
									"http://pyravlos1.di.uoa.gr:8080/geoqa/Query");
							links.add(l);
							System.out.println("Question: " + myQuestion + " ::: " + resultString[i].trim()
									+ "== index : " + l.begin + " : " + l.end +"\t"+i+"\t 2 resultStringLength: "+resultString.length);
						}
						System.out.println("<----- Result ----->");

					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				cnt++;
			}
			// STEP4: Push the result of the component to the triplestore
			String sparql;
			logger.info("Apply vocabulary alignment on outgraph");
			for (Link l : links) {
				sparql = "prefix qa: <http://www.wdaqua.eu/qa#> "
						+ "prefix oa: <http://www.w3.org/ns/openannotation/core/> "
						+ "prefix xsd: <http://www.w3.org/2001/XMLSchema#> " + "INSERT { " + "GRAPH <"
						+ myQanaryQuestion.getOutGraph() + "> { " + "  ?a a qa:AnnotationOfInstance . "
						+ "  ?a oa:hasTarget [ " + "           a    oa:SpecificResource; "
						+ "           oa:hasSource    <" + myQanaryQuestion.getUri() + ">; "
						+ "           oa:hasSelector  [ " + "                    a oa:TextPositionSelector ; "
						+ "                    oa:start \"" + l.begin + "\"^^xsd:nonNegativeInteger ; "
						+ "                    oa:end  \"" + l.end + "\"^^xsd:nonNegativeInteger;  "
						+ "					   oa:linkcount \"" + l.linkCount + "\"^^xsd:nonNegativeInteger "
						+ "           ] " + "  ] . " + "  ?a oa:hasBody <" + l.link + "> ;"
						+ "     oa:annotatedBy <http://TagMeDisambiguate> ; "
						+ "	    oa:AnnotatedAt ?time  " + "}} " + "WHERE { " + "BIND (IRI(str(RAND())) AS ?a) ."
						+ "BIND (now() as ?time) " + "}";
				logger.info("Sparql query {}", sparql);
				System.out.println("Sparql : "+ sparql);
				myQanaryUtils.updateTripleStore(sparql, myQanaryQuestion.getEndpoint().toString());
			}
			long estimatedTime = System.currentTimeMillis() - startTime;
			logger.info("Time {}", estimatedTime);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return myQanaryMessage;
	}
	class Spot {
		public int begin;
		public int end;
	}

	class Link {
		public int begin;
		public int end;
		public String link;
		public int linkCount;
	}
}
