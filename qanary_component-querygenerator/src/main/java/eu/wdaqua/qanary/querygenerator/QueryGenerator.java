package eu.wdaqua.qanary.querygenerator;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jena.base.Sys;
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

import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
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
public class QueryGenerator extends QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(QueryGenerator.class);
	public static ArrayList<DependencyTreeNode> myTreeNodes = new ArrayList<DependencyTreeNode>();
	public static ArrayList<DependencyTreeNode> myTreeNodes1 = new ArrayList<DependencyTreeNode>();
	public static List<List<Property>> propertiesList = new ArrayList<List<Property>>();
	public static List<String> postagListsInorderTree = new ArrayList<String>();
	public static List<List<Concept>> concpetsLists = new ArrayList<List<Concept>>();
	public static List<List<SpatialRelation>> relationsList = new ArrayList<List<SpatialRelation>>();
	public static List<List<Entity>> instancesList = new ArrayList<List<Entity>>();
	public static List<List<TimeAnnotation>> timeAnnotationList = new ArrayList<List<TimeAnnotation>>();
	public static ArrayList<MissionPlatform> missionPlatformList = new ArrayList<MissionPlatform>();
	public static String questionText = "", questionTextL = "";

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
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		for (CoreMap sentence : sentences) {
			// Iterate over all tokens in a sentence
			for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
				// Retrieve and add the lemma for each word into the
				// list of lemmas
				lemmas.add(token.get(LemmaAnnotation.class));
				lemmetizedQuestion += token.get(LemmaAnnotation.class) + " ";
			}
		}
		return lemmetizedQuestion;
	}
	public static String runSparqlOnEndpoint(String sparqlQuery, String endpointURI) {

		org.apache.jena.query.Query query = QueryFactory.create(sparqlQuery);
		System.out.println("sparql query :" + query.toString());
		QueryExecution exec = QueryExecutionFactory.sparqlService(endpointURI, query);
		ResultSet results = ResultSetFactory.copyResults(exec.execSelect());

		if (!results.hasNext()) {
			System.out.println("There is no next!");
		} else {
			while (results.hasNext()) {

				QuerySolution qs = results.next();
				ArrayList<Query> allQueriesList = new ArrayList<Query>();
				String x = qs.get("x").toString();
				System.out.println("runSparqlOnEndpoint x= " + x);
				return x;

			}
		}
		return null;
	}

	public static Boolean answerAvailable(String concept, String instance, String relation) {
		Boolean found = false;
		System.out.println("===============Calling answer available========================");
		// parse the csv into a list of arrays
		ArrayList<String[]> ls = new ArrayList<String[]>();
		String fileName = "/src/main/resources/final_table_yago.csv";
		File file = new File(fileName);

		try {
			Scanner inputStream = new Scanner(file);
			while (inputStream.hasNext()) {
				String data = inputStream.next();
				String[] arr = data.split(",");
				ls.add(arr);
			}
			inputStream.close();
		} catch (FileNotFoundException e) {
			System.out.println("Csv File not found");
		}
		System.out.println("Concept: " + concept + "\t Relation: " + relation + "\t Instance: " + instance);
		// remove from list lines without the specific concept and relation
		for (int i = 0; i < ls.size(); i++) {
			String line[] = ls.get(i);
			if (line[0].compareTo(concept) < 0 || line[1].compareTo(relation) < 0) {
				ls.remove(line);
				i = i - 1;
			}
		}

		// find the type of the instance and compare with those in list
		if (!ls.isEmpty()) {
			System.out.println("Getting in table not empty==============");
			String endpoint = "http://pyravlos1.di.uoa.gr:8890/sparql";// "https://linkeddata1.calcul.u-psud.fr/sparql";
			QueryExecution objectToExec;

			String query = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
					+ " PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " + "SELECT  ?type " + " WHERE { " + " <"
					+ instance + ">  rdf:type ?type. " + "}";

			// update code below for finding entry in table
			System.out.println("yago Query : " + query);
			objectToExec = QueryExecutionFactory.sparqlService(endpoint, query);
			ResultSet r = objectToExec.execSelect();
			while (r.hasNext()) {
				QuerySolution s = r.next();
				String uria = s.get("type").toString();

				for (String[] line : ls) {
					// System.out.println("uria: "+uria);
					if (uria.contains(line[2])) {
						// found = true;
						// break;
						return true;
					}
				}
			}
		}

		return found;
	}

	public static Boolean checkNeighbours(Concept con, Entity ent) {
		System.out.println("===============Calling checkNeighbours========================");
//		System.out.println("con: "+con.link +"\t ent: "+ent.uri);
//		System.out.println("Concept :");
//		con.print();

		for (int i = 0; i < myTreeNodes.size(); i++) {
			String treeConcept = "";
			String treeEntity = "";
			if (myTreeNodes.get(i).annotationsConcepts.size() > 0) {
				treeConcept = myTreeNodes.get(i).annotationsConcepts.get(0);
			}
			if (myTreeNodes.get(i).annotationsInstance.size() > 0) {
				treeEntity = myTreeNodes.get(i).annotationsInstance.get(0);
			}
			if (!treeConcept.equals("") && con.link.contains(treeConcept)) {
				if (i < (myTreeNodes.size() - 1) && myTreeNodes.get(i + 1).annotationsInstance.size() > 0) {
					if (ent.uri.contains(myTreeNodes.get(i + 1).annotationsInstance.get(0))) {
						System.out.println("return true");
						return true;
					}
				} else if (i > 0 && myTreeNodes.get(i - 1).annotationsInstance.size() > 0) {
					if (ent.uri.contains(myTreeNodes.get(i - 1).annotationsInstance.get(0))) {
						System.out.println("return true");
						return true;
					}
				}
			}
		}

		System.out.println("concept: " + con.link + ":" + con.begin + " : " + con.end + " ===== " + "entity : "
				+ ent.uri + " : " + ent.begin + " : " + ent.end);
		if ((con.end + 1) == ent.begin) // i.e. River Thames
			return true;
		else if ((ent.end + 1) == con.begin) // i.e. Thames River
			return true;
		else {
			if (con.begin <= ent.begin) { // i.e. Edinburgh Castle in which "Edinburgh Castle" is Instance and Castle is
				// Concept/Class
				if (con.end > ent.begin) {
					return true;
				}
			}
			if (ent.begin <= con.begin) { // i.e. River Shannon in which "Shannon" is Instance(river shannon) and River
				// is Concept/class
				if (ent.end > con.begin) {
					return true;
				}
			}

		}
		System.out.println("return false=================== ");
		return false;
	}

	public static Boolean checkTypes(Concept con, Entity ent) {
		System.out.println("===============Calling checkTypes========================");
		String endpoint = "http://pyravlos1.di.uoa.gr:8890/sparql";// "https://linkeddata1.calcul.u-psud.fr/sparql";
		QueryExecution objectToExec;

		// get type of instance Make sure that we query only DBpedia for DBpedia classes
		// and OSM/GADM for OSM/GADM classes.
		if (con.link.contains("http://yago-knowledge.org") && ent.uri.contains("http://yago-knowledge.org")) {
			System.out.println("concept: " + con.link + " ===== " + "entity : " + ent.uri);
			String query = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
					+ "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
					+ "PREFIX dbo: <http://dbpedia.org/ontology/>" + "SELECT  ?type " + "WHERE { "
					+ "  ?x rdfs:label \"" + ent.namedEntity + "\"@eng. " + "  ?x rdf:type ?type. " + "}";

			// must query db
			objectToExec = QueryExecutionFactory.sparqlService(endpoint, query);
			ResultSet r = objectToExec.execSelect();
			if (r.hasNext()) {
				QuerySolution s = r.next();

				if (s.contains(con.link))
					return true;
			}
			return false;
		} else if ((con.link.contains("gadm") && ent.uri.contains("gadm"))
				|| (con.link.contains("osm") && ent.uri.contains("osm"))) {
			System.out.println("concept: " + con.link + " ===== " + "entity : " + ent.uri);
			String host = "pyravlos1.di.uoa.gr";
			Integer port = 8080;
			String appName = "geoqa/Query";
			String query = "select ?x where { <" + ent.uri + "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> "
					+ con.link + "}";
			String format = "TSV";
			SPARQLEndpoint endpointosm = new SPARQLEndpoint(host, port, appName);
			try {
				EndpointResult result = endpointosm.query(query,
						(stSPARQLQueryResultFormat) stSPARQLQueryResultFormat.valueOf(format));
				String resultString[] = result.getResponse().replaceAll("\n", "\n\t").split("\n");
				if (resultString.length > 2) {
					System.out.println("Return true====================");
					return true;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("Return true====================");
			return false;
		} else
			return false;
	}

	public static void primitiveTraversal(Tree tree, List<Concept> cons, List<Entity> ents, List<String> rels) {

//		nodeT.m_parent = tree.parent();
		for (Tree childTree : tree.children()) {
//			if (childTree.isLeaf())
			primitiveTraversal(childTree, cons, ents, rels);
		}
		boolean flg = false;
//		TreeNodeDependency nodeT = (TreeNodeDependency) tree;
		DependencyTreeNode nodeT = new DependencyTreeNode();
		nodeT.m_name = tree.nodeString();

		if (tree.isLeaf()) {

//			System.out.println("tree node: "+tree.nodeString());
//			System.out.println(
//					"cons.size(): " + cons.size() + "\trels size: " + rels.size() + "\tents.size(): " + ents.size());
			for (Concept con : cons) {

				if (con.label.toLowerCase().equals(tree.nodeString().toLowerCase())) {
//					System.out.println(" concepts : " + con.label+" : ");
					nodeT.addAnnotationConcept(con.link);
//					System.out.print("C");
					flg = true;
					break;
				}
			}
			for (Entity ent : ents) {

				String entString = ent.namedEntity;
				if (entString.contains(" ")) {
//					System.out.println("Space");

				}

				if (ent.namedEntity.toLowerCase().contains(tree.nodeString().toLowerCase())) {
//					System.out.println(" namedEntity: " + ent.namedEntity+" : ");
					nodeT.addAnnotationInstance(ent.uri);
					flg = true;
//					System.out.print("I");
					break;
				}
			}
//			if(cons.contains(tree.nodeString())) {
//				System.out.println("C");
//			}
//			if(ents.contains(tree.nodeString())) {
//				System.out.println("I");
//			}

			if (rels.contains(tree.nodeString())) {
				nodeT.addAnnotationRelation("R");
				flg = true;
//				System.out.print("R");
			}
			if (flg) {
				myTreeNodes.add(nodeT);
			}

//			dependencyTreeNodeList.add(nodeT);
//			System.out.println("tree : " + tree.nodeString());
//			System.out.println("tree parent node: "+tree.parent().nodeString());
		}
	}

	public static String walkTreeAndGetPattern1() {
		String identifiedPattern = "";
//		System.out.println("MyTreeNode Size : " + myTreeNodes1.size());
		for (DependencyTreeNode tn : myTreeNodes1) {

			postagListsInorderTree.add(tn.posTag);
			if(tn.timeAnnotations.size() >0){
				identifiedPattern += "T";
				timeAnnotationList.add(tn.timeAnnotations);
			}
			if (tn.relationList.size() > 0) {
				identifiedPattern += "R";
				relationsList.add(tn.relationList);
			}
			if (tn.entityList.size() > 0) {
				identifiedPattern += "I";
				instancesList.add(tn.entityList);
			} else if (tn.conceptList.size() > 0) {
				identifiedPattern += "C";
				concpetsLists.add(tn.conceptList);
			} else if (tn.propertyList.size() > 0) {
				identifiedPattern += "P";
				propertiesList.add(tn.propertyList);
			}
//			System.out.println("postag: " + tn.posTag);
		}

		return identifiedPattern;
	}

	public static void walkTreeAndMergeNodes() {

		for (int j = 0; j < myTreeNodes1.size() - 1; j++) {
			DependencyTreeNode tnj = myTreeNodes1.get(j);
			DependencyTreeNode tnj1 = myTreeNodes1.get(j + 1);

			if ((tnj.posTag.equalsIgnoreCase("NN") || tnj.posTag.equalsIgnoreCase("NNP")
					|| tnj.posTag.equalsIgnoreCase("NNPS"))
					&& (tnj1.posTag.equalsIgnoreCase("NN") || tnj1.posTag.equalsIgnoreCase("NNP")
					|| tnj1.posTag.equalsIgnoreCase("NNPS"))) {
				if ((tnj.conceptList.size() > 0 || tnj.entityList.size() > 0)
						&& (tnj1.conceptList.size() > 0 || tnj1.entityList.size() > 0)) {
					tnj.m_name += " " + tnj1.m_name;
					tnj.endIndex = tnj1.endIndex;
					if (tnj1.conceptList.size() > 0) {
						tnj.conceptList.addAll(tnj1.conceptList);
					}
					if (tnj1.entityList.size() > 0) {
						tnj.entityList.addAll(tnj1.entityList);
					}
					myTreeNodes1.remove(j + 1);
					j = j - 1;
				}
			}

//			if (tnj.relationList.size() > 0 && tnj1.entityList.size() > 0) {
//				tnj.m_name += " " + tnj1.m_name;
//				tnj.endIndex = tnj1.endIndex;
//				if (tnj1.entityList.size() > 0) {
//					tnj.entityList.addAll(tnj1.entityList);
//				}
//				myTreeNodes1.remove(j + 1);
//				j = j - 1;
//				continue;
//			}
//			if (tnj1.relationList.size() > 0 && tnj.entityList.size() > 0) {
//				tnj.m_name += " " + tnj1.m_name;
//				tnj.endIndex = tnj1.endIndex;
//				if (tnj.entityList.size() > 0) {
//					tnj1.entityList.addAll(tnj.entityList);
//				}
//				myTreeNodes1.remove(j + 1);
//				j = j - 1;
//				continue;
//			}
//			if (tnj.conceptList.size() == tnj1.conceptList.size()) {
//				boolean flg = false;
//				for (Concept con : tnj.conceptList) {
//					if (tnj1.conceptList.contains(con)) {
//						System.out.println("+++++++++++++++ Inside same Concept +++++++++++++++");
//						flg = true;
//						break;
//					}
//				}
//				if (flg) {
//					tnj.m_name += " " + tnj1.m_name;
//					tnj.endIndex = tnj1.endIndex;
//					if (tnj1.entityList.size() > 0) {
//						tnj.entityList.addAll(tnj1.entityList);
//					}
//					myTreeNodes1.remove(j + 1);
//					j = j - 1;
//					continue;
//				}
//
//			}
			if (tnj.entityList.size() == tnj1.entityList.size()) {
				boolean flg = false;
				for (Entity ent : tnj.entityList) {

					if (tnj1.entityList.contains(ent)) {
						System.out.println("+++++++++++++++ Inside same Entity +++++++++++++++");
						flg = true;
						break;
					}
				}
				if (flg) {
					tnj.m_name += " " + tnj1.m_name;
					tnj.endIndex = tnj1.endIndex;
					if (tnj1.conceptList.size() > 0) {
						tnj.conceptList.addAll(tnj1.conceptList);
					}
					myTreeNodes1.remove(j + 1);
					j = j - 1;
					continue;
				}
			}

//			if (tnj1.posTag.equalsIgnoreCase(",")) {
//				if (myTreeNodes1.size() >= (j + 2)) {
//
//					DependencyTreeNode tnj2 = myTreeNodes1.get(j + 2);
//					if ((tnj.posTag.equalsIgnoreCase("NN") || tnj.posTag.equalsIgnoreCase("NNP")
//							|| tnj.posTag.equalsIgnoreCase("NNPS"))
//							&& (tnj2.posTag.equalsIgnoreCase("NN") || tnj2.posTag.equalsIgnoreCase("NNP")
//									|| tnj2.posTag.equalsIgnoreCase("NNPS"))) {
//						if ((tnj.conceptList.size() > 0 || tnj.entityList.size() > 0)
//								&& (tnj2.conceptList.size() > 0 || tnj2.entityList.size() > 0)) {
//							tnj.m_name += " " + tnj2.m_name;
//							tnj.endIndex = tnj2.endIndex;
//							if (tnj2.conceptList.size() > 0) {
//								tnj.conceptList.addAll(tnj2.conceptList);
//							}
//							if (tnj2.entityList.size() > 0) {
//								tnj.entityList.addAll(tnj2.entityList);
//							}
//							myTreeNodes1.remove(j + 1);
//							myTreeNodes1.remove(j+2);
//							j = j - 1;
//						}
//					}
//				}
//			}

		}
	}

	public static void firstTraversal(Tree tree) {
		for (Tree childTree : tree.children()) {
			firstTraversal(childTree);
		}
		DependencyTreeNode nodeT = new DependencyTreeNode();
		nodeT.m_name = tree.nodeString();

		if (tree.isLeaf()) {

			int ind;
			ind = questionTextL.indexOf(nodeT.m_name);
			if (ind != -1) {
				nodeT.startIndex = ind;
				nodeT.endIndex = ind + nodeT.m_name.length() - 1;
			} else {
				ind = questionText.indexOf(nodeT.m_name);
				if (ind != -1) {
					nodeT.startIndex = ind;
					nodeT.endIndex = ind + nodeT.m_name.length() - 1;
				}
			}

			nodeT.posTag = getPosTagofWord(questionTextL, tree.nodeString());
			myTreeNodes1.add(nodeT);
		}
	}

	public static String getPosTagofWord(String documentText, String word) {
		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos,lemma");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		String postags = "";
		String lemmetizedQuestion = "";
		// Create an empty Annotation just with the given text
		Annotation document = new Annotation(documentText);
		// run all Annotators on this text
		pipeline.annotate(document);
		// Iterate over all of the sentences found
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		for (CoreMap sentence : sentences) {
			// Iterate over all tokens in a sentence
			for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
				String pos = token.get(PartOfSpeechAnnotation.class);
				if (token.originalText().equalsIgnoreCase(word)) {
					postags = pos;
				}
			}
		}
		return postags;
	}

	public static void printParseTree1() {
		System.out.println("parse tree annotated elements :");
		for (DependencyTreeNode tn : myTreeNodes1) {
			System.out.println("------------------------------------------------------------------");
			System.out.println("Tree node: " + tn.m_name);
			System.out.println("Start index : " + tn.startIndex + "\t end index: " + tn.endIndex);
			System.out.println("Postag : " + tn.posTag);
			System.out.println("No. of Concepts: " + tn.conceptList.size());
			System.out.println("No. of Instances: " + tn.entityList.size());
			System.out.println("No. of Relations: " + tn.relationList.size());
			System.out.println("No. of Properties: " + tn.propertyList.size());
			System.out.println("No. of Time annotations: " + tn.timeAnnotations.size());
//			System.out.println("Concepts node: " + tn.annotationsConcepts.toString());
//			System.out.println("Relations node: " + tn.annotationsRelations.toString());
//			System.out.println("Instances node: " + tn.annotationsInstance.toString());
		}
	}

	public static void annotateTreenode(Concept con) {
//		System.out.println("Concept start: "+con.begin+"\t end: "+con.end);
		for (DependencyTreeNode tn : myTreeNodes1) {
//			System.out.println("tree node start: "+tn.startIndex +"\t end: "+tn.endIndex);
			if (tn.startIndex < con.end && tn.endIndex > con.begin) {

				if (con.label.contains(tn.m_name) && tn.m_name.length() > 1) { // con.label.equalsIgnoreCase(tn.m_name))
					// {
					if (!(con.label.length() > tn.m_name.length())) {
						tn.conceptList.add(con);
					}
				}
			}
		}
	}

	public static void annotateTreenode(Entity ent) {
		System.out.println("Entity start: " + ent.begin + "\t end: " + ent.end);
		for (DependencyTreeNode tn : myTreeNodes1) {
			System.out.println("tree node start: " + tn.startIndex + "\t end: " + tn.endIndex);
			if (tn.startIndex < ent.end && tn.endIndex > ent.begin && tn.m_name.length() > 1) {
				if (ent.namedEntity.contains(tn.m_name)) { // ent.namedEntity.equalsIgnoreCase(tn.m_name)) {
					tn.entityList.add(ent);
				}
			}
		}
	}

	public static void annotateTreenode(Property property) {
		System.out.println("Property start: " + property.begin + "\t end: " + property.end);
		for (DependencyTreeNode tn : myTreeNodes1) {
			System.out.println("tree node start: " + tn.startIndex + "\t end: " + tn.endIndex);
			if (tn.startIndex < property.end && tn.endIndex > property.begin && tn.m_name.length() > 1) {
				if (property.uri.toLowerCase().contains(tn.m_name.toLowerCase())) { // ent.namedEntity.equalsIgnoreCase(tn.m_name))
					// {
					tn.propertyList.add(property);
				}
			}
		}
	}

	public static void annotateTreenode(SpatialRelation sr) {
		System.out.println("sr.relation : " + sr.relation);
		for (DependencyTreeNode tn : myTreeNodes1) {
			if (tn.m_name.contains(sr.relation) || (tn.m_name.contains("most") && sr.relation.contains("most"))) {
				if (!(sr.relation.length() < tn.m_name.length()))

					tn.relationList.add(sr);
			}
		}
	}

	public static void annotateTreeNode(TimeAnnotation tma){
		System.out.println("tma : "+ tma.getString());
		for(DependencyTreeNode tn: myTreeNodes1){
			String splitted[] = tma.text.split(" ");
			for(String txt: splitted){
				if(tn.m_name.toLowerCase().contains(txt.toLowerCase())){
					tn.timeAnnotations.add(tma);
				}
			}
		}
	}

	public static List<String> getW(String documentText) {
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
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		for (CoreMap sentence : sentences) {
			// Iterate over all tokens in a sentence
			for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
				String pos = token.get(PartOfSpeechAnnotation.class);
				if (pos.contains("WRB")) {
					postags.add(token.get(LemmaAnnotation.class));
				}
			}
		}
		return postags;
	}

	public static String create_Mission_metadata_Triples(){
		String triples = "";
		for(MissionPlatform msp : missionPlatformList){
			triples += " ?x <http://ws.eodias.eu/metadata/attribute#mission> <"+msp.mission+"> . ";
			if(msp.containsPlatform){
				triples += " ?x <http://ws.eodias.eu/metadata/attribute#platform> <"+msp.platform+"> . ";
			}
			if(msp.containsPtype){
				triples += " ?x <http://ws.eodias.eu/metadata/attribute#productType> <"+msp.productType+"> . ";
			}
		}
		System.out.println("generated Mission triples : " + triples);
		return  triples;
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
		logger.info("process: {}", myQanaryMessage);

		String detectedPattern = "";
		ArrayList<Query> allQueriesList = new ArrayList<Query>();
		List<String> properties = new ArrayList<String>();
		List<String> propertiesValue = new ArrayList<String>();
		List<Integer> indexOfConcepts = new ArrayList<Integer>();
		List<Integer> indexOfInstances = new ArrayList<Integer>();
		List<Property> propertiesList = new ArrayList<Property>();
		Map<String, List<Integer>> mapOfRelationIdex = new HashMap<String, List<Integer>>();
		Map<Integer, String> patternForQueryGeneration = new HashMap<Integer, String>();
		Map<Integer, String> mapOfGeoRelation = new TreeMap<Integer, String>();
		Map<Integer, List<Concept>> sameConcepts = new HashMap<Integer, List<Concept>>();
		Map<Integer, List<Entity>> sameInstances = new HashMap<Integer, List<Entity>>();
		List<TimeAnnotation> timeAnnotations = new ArrayList<TimeAnnotation>();
		try {
			logger.info("store data in graph {}",
					myQanaryMessage.getValues().get(new URL(myQanaryMessage.getEndpoint().toString())));
			// TODO: insert data in QanaryMessage.outgraph

			QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);
			QanaryQuestion<String> myQanaryQuestion = this.getQanaryQuestion(myQanaryMessage);
			String myQuestion = myQanaryQuestion.getTextualRepresentation();
			String myQuestionNL = myQuestion;
			myQuestion = lemmatize(myQuestion);
			questionText = myQuestionNL;
			questionTextL = myQuestion;
			logger.info("Question: {}", myQuestion);

			Properties props = new Properties();
			props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse");
			StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
			Annotation document = new Annotation(myQuestion);
			pipeline.annotate(document);
			Tree tree = null;
			List<CoreMap> sentences = document.get(SentencesAnnotation.class);
			for (CoreMap sentence : sentences) {
				// traversing the words in the current sentence
				// a CoreLabel is a CoreMap with additional token-specific methods
				for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
					// this is the text of the token
					String word = token.get(TextAnnotation.class); // this is the POS tag of the token
					String pos = token.get(PartOfSpeechAnnotation.class); // this is the NER label of the token
					String ne = token.get(NamedEntityTagAnnotation.class);
				}
				// this is the parse tree of the current sentence
				tree = sentence.get(TreeAnnotation.class);

				tree.pennPrint();

				// this is the Stanford dependency graph of the current sentence
				SemanticGraph dependencies = sentence
						.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);

				dependencies.prettyPrint();

			}

			firstTraversal(tree);
			String sparql;
			boolean dbpediaPropertyFlag = false;
			boolean dbpediaPropertyValueFlag = false;
			Entity ent = new Entity();
			Concept concept = new Concept();
			List<Concept> concepts = new ArrayList<>();
			List<String> geoSPATIALRelations = new ArrayList<String>();
			List<Entity> entities = new ArrayList<Entity>();
			List<String> relationKeywords = new ArrayList<String>();
			ResultSet r;
			String geoRelation = null;
			String thresholdDistance = "";
			String unitDistance = "";
			List<String> distanceUnits = new ArrayList<String>();
			distanceUnits.add("kilometer");
			distanceUnits.add("km");
			distanceUnits.add("metre");
			distanceUnits.add("meter");
			String geoSparqlQuery = "";// prefixs + selectClause;
			boolean thresholdFlag = false;

			relationKeywords.add("in");
			relationKeywords.add("within");
			relationKeywords.add("of");
			relationKeywords.add("inside");
			relationKeywords.add("contains");
			relationKeywords.add("includes");
			relationKeywords.add("have");
			relationKeywords.add("above");
			relationKeywords.add("north");
			relationKeywords.add("below");
			relationKeywords.add("south");
			relationKeywords.add("right");
			relationKeywords.add("east");
			relationKeywords.add("west");
			relationKeywords.add("left");
			relationKeywords.add("near");
			relationKeywords.add("nearby");
			relationKeywords.add("close");
			relationKeywords.add("at most");
			relationKeywords.add("around");
			relationKeywords.add("less than");
			relationKeywords.add("at least");
			relationKeywords.add("center");
			relationKeywords.add("middle");
			relationKeywords.add("border");
			relationKeywords.add("outskirts");
			relationKeywords.add("boundary");
			relationKeywords.add("surround");
			relationKeywords.add("adjacent");
			relationKeywords.add("crosses");
			relationKeywords.add("cross");
			relationKeywords.add("intersect");
			relationKeywords.add("flows");
			relationKeywords.add("flow");

			// Identify distance threshold
			thresholdDistance = myQuestion.replaceAll("[^-\\d]+", "");
			logger.info("Question without numbers: {}", myQuestion.replaceAll("[^-\\d]+", ""));
			if (!thresholdDistance.equals("")) {
				for (String tempUnit : distanceUnits) {

					Pattern p = Pattern.compile("\\b" + tempUnit + "\\b", Pattern.CASE_INSENSITIVE);
					Matcher m = p.matcher(myQuestion.replaceAll(thresholdDistance, ""));
					if (m.find()) {
						unitDistance = tempUnit;
						break;
					}
				}

				if (unitDistance.equalsIgnoreCase("km") || unitDistance.equalsIgnoreCase("kilometer")
						|| unitDistance.equalsIgnoreCase("kms")) {

					thresholdDistance = thresholdDistance + "000";
					thresholdFlag = true;
				}
				if (unitDistance.contains("meter") || unitDistance.contains("metre")) {
					thresholdFlag = true;
				}
			}

			// property
			sparql = "PREFIX qa: <http://www.wdaqua.eu/qa#> "
					+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> "
					+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> "//
					+ "SELECT  ?uri ?start ?end " + "FROM <" + myQanaryQuestion.getInGraph() + "> " //
					+ "WHERE { " //
					+ "  ?a a qa:AnnotationOfRelation . " + "  ?a oa:hasTarget [ " + " a    oa:SpecificResource; "
					+ "           oa:hasSource    ?q; " + "				oa:hasSelector  [ " //
					+ "			         a        oa:TextPositionSelector ; " //
					+ "			         oa:start ?start ; " //
					+ "			         oa:end   ?end " //
					+ "		     ] " //
					+ "  ]; " + "     oa:hasValue ?uri ;oa:AnnotatedAt ?time} order by(?time)";

			r = myQanaryUtils.selectFromTripleStore(sparql);

			while (r.hasNext()) {
				QuerySolution s = r.next();
				Property property = new Property();
				properties.add(s.getResource("uri").getURI());
				property.begin = s.getLiteral("start").getInt();
				property.end = s.getLiteral("end").getInt();
				property.label = myQuestionNL.substring(property.begin,property.end);
				property.uri = s.getResource("uri").getURI();

				if (property.end > 0) {
					propertiesList.add(property);
					dbpediaPropertyFlag = true;
					annotateTreenode(property);
				}
				logger.info("DBpedia (property) uri info {} label {}", s.getResource("uri").getURI(), property.label);
			}

			System.out.println("total properties found : " + propertiesList.size());

			// String newGeoSparqlQuery = prefixs + selectClause;
			// TODO: refactor this to an enum or config file
			Map<String, String> mappingOfGeospatialRelationsToGeosparqlFunctions = new HashMap<>();
			mappingOfGeospatialRelationsToGeosparqlFunctions.put("geof:sfWithin", "within");
			mappingOfGeospatialRelationsToGeosparqlFunctions.put("geof:sfCrosses", "crosses");
			mappingOfGeospatialRelationsToGeosparqlFunctions.put("geof:distance", "near");
			mappingOfGeospatialRelationsToGeosparqlFunctions.put("strdf:above", "north");
			mappingOfGeospatialRelationsToGeosparqlFunctions.put("strdf:above_left", "north_west");
			mappingOfGeospatialRelationsToGeosparqlFunctions.put("strdf:above_right", "north_east");
			mappingOfGeospatialRelationsToGeosparqlFunctions.put("strdf:below", "south");
			mappingOfGeospatialRelationsToGeosparqlFunctions.put("strdf:below_right", "south_east");
			mappingOfGeospatialRelationsToGeosparqlFunctions.put("strdf:below_left", "south_west");
			mappingOfGeospatialRelationsToGeosparqlFunctions.put("strdf:right", "east");
			mappingOfGeospatialRelationsToGeosparqlFunctions.put("strdf:left", "west");
			mappingOfGeospatialRelationsToGeosparqlFunctions.put("postgis:ST_Centroid", "center");
			mappingOfGeospatialRelationsToGeosparqlFunctions.put("geof:boundary", "boundry");
			// implement the CRI pattern

			// 1. concepts: Retrieve via SPARQL the concepts identified for the
			// given question

			// 2. relation in the question: Retrieves the spatial function
			// supported by the GeoSPARQL from the graph for e.g.
			// fetch the geospatial relation identifier
			sparql = "PREFIX qa: <http://www.wdaqua.eu/qa#> "
					+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> "
					+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> "//
					+ "SELECT ?geoRelation ?start ?relString " + "FROM <" + myQanaryQuestion.getInGraph() + "> " //
					+ "WHERE { " //
					+ "    ?a a qa:AnnotationOfRelation . " + "?a oa:hasTarget [ "
					+ "		     a               oa:SpecificResource; " //
					+ "		     oa:hasSource    ?q; " //
					+ "	         oa:hasRelation  [ " //
					+ "			         a        oa:GeoRelation ; " //
					+ "			         oa:geoRelation ?geoRelation ; " //
					+ "	         		 oa:hasSelector  [ " //
					+ "			         		a        oa:TextPositionSelector ; " //
					+ "			         		oa:start ?start ; " //
					+ "                          oa:relString ?relString ;" + "		     ] " //
					+ "		     ] " //
					+ "    ] ; " //
					+ "} " //
					+ "ORDER BY ?start ";

			r = myQanaryUtils.selectFromTripleStore(sparql);

			while (r.hasNext()) {
				QuerySolution s = r.next();
				logger.info("found relation : {} at {}", s.getResource("geoRelation").getURI().toString(),
						s.getLiteral("start").getInt());
				String geoSpatialRelation = s.getResource("geoRelation").getURI().toString();
				int geoSpatialRelationIndex = s.getLiteral("start").getInt();
				String relStringQuestion = s.getLiteral("relString").getString();
				geoSPATIALRelations.add(geoSpatialRelation);
				System.out.println("geoSpatialRelation : " + geoSpatialRelation);
				if (mapOfRelationIdex.size() == 0) {
					List<Integer> indexes = new ArrayList<Integer>();
					indexes.add(geoSpatialRelationIndex);
					mapOfRelationIdex.put(geoSpatialRelation, indexes);

					SpatialRelation sr = new SpatialRelation();
					sr.relation = relStringQuestion;
					sr.index = geoSpatialRelationIndex;
					sr.relationFunction = geoSpatialRelation;
					annotateTreenode(sr);
				} else {
					if (mapOfRelationIdex.keySet().contains(geoSpatialRelation)) {
						if (geoSpatialRelation.contains("geof:sfWithin")) {
							if (mapOfRelationIdex.keySet().contains("strdf:left")
									|| mapOfRelationIdex.keySet().contains("strdf:right")
									|| mapOfRelationIdex.keySet().contains("strdf:above")
									|| mapOfRelationIdex.keySet().contains("strdf:below")) {
								continue;
							}
						}
						List<Integer> indexes = mapOfRelationIdex.remove(geoSpatialRelation);
						indexes.add(geoSpatialRelationIndex);
						mapOfRelationIdex.put(geoSpatialRelation, indexes);
						SpatialRelation sr = new SpatialRelation();
						sr.relation = relStringQuestion;
						sr.index = geoSpatialRelationIndex;
						sr.relationFunction = geoSpatialRelation;
						annotateTreenode(sr);
					} else {
						if (geoSpatialRelation.contains("geof:sfWithin")) {
							if (mapOfRelationIdex.keySet().contains("strdf:left")
									|| mapOfRelationIdex.keySet().contains("strdf:right")
									|| mapOfRelationIdex.keySet().contains("strdf:above")
									|| mapOfRelationIdex.keySet().contains("strdf:below")) {
								continue;
							}
						}
						List<Integer> indexes = new ArrayList<Integer>();
						indexes.add(geoSpatialRelationIndex);
						mapOfRelationIdex.put(geoSpatialRelation, indexes);
						SpatialRelation sr = new SpatialRelation();
						sr.relation = relStringQuestion;
						sr.index = geoSpatialRelationIndex;
						sr.relationFunction = geoSpatialRelation;
						annotateTreenode(sr);
					}
				}
			}

			// map the given relation identifier to a GeoSPARQL function
			String geosparqlFunction = mappingOfGeospatialRelationsToGeosparqlFunctions.get(geoRelation);

			// STEP 3.0 Retrieve concepts from Triplestore

			sparql = "PREFIX qa: <http://www.wdaqua.eu/qa#> "
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

				conceptTemp.label = myQuestion.substring(conceptTemp.begin, conceptTemp.end);
				// geoSparqlQuery += "" + conceptTemplate.replace("poiURI",
				// conceptTemp.link).replaceAll("poi",
				// "poi" + conceptTemp.begin);
				// newGeoSparqlQuery += "" + conceptTemplate.replace("poiURI",
				// conceptTemp.link).replaceAll("poi",
				// "poi" + conceptTemp.begin);
				indexOfConcepts.add(conceptTemp.begin);
				concepts.add(conceptTemp);
				annotateTreenode(conceptTemp);
				logger.info("Concept start {}, end {}, URI {}", conceptTemp.begin, conceptTemp.end, conceptTemp.link);

			}

			// 3.1 Instance: Retrieve Starting and ending Index of the Instance
			// (Point of Interest) as well as URI
			sparql = "PREFIX qa: <http://www.wdaqua.eu/qa#> "
					+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> "
					+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> "//
					+ "SELECT ?start ?end ?lcount ?uri " + "FROM <" + myQanaryQuestion.getInGraph() + "> " //
					+ "WHERE { " //
					+ "    ?a a qa:AnnotationOfInstance . " + "?a oa:hasTarget [ "
					+ "		     a               oa:SpecificResource; " //
					+ "		     oa:hasSource    ?q; " //
					+ "	         oa:hasSelector  [ " //
					+ "			         a        oa:TextPositionSelector ; " //
					+ "			         oa:start ?start ; " //
					+ "			         oa:end   ?end ; " //
					+ "			         oa:linkcount   ?lcount " + "		     ] " //
					+ "    ] . " //
					+ " ?a oa:hasBody ?uri ; " + "    oa:annotatedBy ?annotator " //
					+ "} " + "ORDER BY ?start ";

			r = myQanaryUtils.selectFromTripleStore(sparql);

			while (r.hasNext()) {
				QuerySolution s = r.next();

				Entity entityTemp = new Entity();
				entityTemp.begin = s.getLiteral("start").getInt();

				entityTemp.end = s.getLiteral("end").getInt();

				entityTemp.uri = s.getResource("uri").getURI();

				System.out.println(
						"uri: " + entityTemp.uri + "\t start: " + entityTemp.begin + "\tend: " + entityTemp.end);

				entityTemp.namedEntity = myQuestionNL.substring(entityTemp.begin, entityTemp.end);

				entityTemp.linkCount = s.getLiteral("lcount").getInt();

				indexOfInstances.add(entityTemp.begin);
				entities.add(entityTemp);
				annotateTreenode(entityTemp);
//				logger.info("Instance start {}, end {}, instance {}, URI{}", entityTemp.begin, entityTemp.end,
//						entityTemp.namedEntity, entityTemp.uri);

			}
			// retriev time annotation
			sparql = "PREFIX qa: <http://www.wdaqua.eu/qa#> "
					+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> "
					+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> "//
					+ "SELECT ?start ?end ?type ?value " + " FROM <" + myQanaryQuestion.getInGraph() + "> " //
					+ " WHERE { " //
					+ "    ?a a qa:AnnotationOfTemporalWord . "
					+ "	   ?a oa:hasTarget [ "
					+ "		     a               oa:SpecificResource; " //
					+ "		     oa:hasSource    ?q; " //
					+ "	         oa:hasSelector  [ " //
					+ "			         a        oa:TextPositionSelector ; " //
					+ "			         oa:start ?start ; " //
					+ "			         oa:end   ?end  " //
					+ "          ]"
					+ "    ] . " //
					+ "    ?a oa:hasType   ?type; " //
					+ "       oa:hasValue  ?value ." //
					+ "} ";

			r = myQanaryUtils.selectFromTripleStore(sparql);

			while (r.hasNext()) {
				QuerySolution s = r.next();
				TimeAnnotation tma = new TimeAnnotation();
				tma.startIndex = s.getLiteral("start").getInt();
				tma.endIndex = s.getLiteral("end").getInt();
				tma.value = s.getLiteral("value").getString();
				tma.type = s.getLiteral("type").getString();
				tma.text = myQuestionNL.substring(tma.startIndex, tma.endIndex);
				System.out.println("type: " + tma.type + "\t start: " + tma.startIndex + "\tend: " + tma.endIndex);
				timeAnnotations.add(tma);
				System.out.println("Time Annotation: "+tma.getString());
				annotateTreeNode(tma);
			}

			sparql = "PREFIX qa: <http://www.wdaqua.eu/qa#> "
					+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> "
					+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> "//
					+ "SELECT ?mission ?platform ?ptype " + " FROM <" + myQanaryQuestion.getInGraph() + "> " //
					+ " WHERE { {" //
					+ "    ?a a qa:AnnotationOfMissionMetadata . "
					+ "    ?a oa:hasMission ?mission . } OPTIONAL { "
					+ "    ?a oa:hasPlatform ?platform. } OPTIONAL { "
					+ "    ?a oa:hasProductType ?ptype. } "
					+ " } ";
			r = myQanaryUtils.selectFromTripleStore(sparql);

			while(r.hasNext()){
				QuerySolution s = r.next();
				MissionPlatform msp = new MissionPlatform();
				if(s.getResource("ptype")!=null){
					msp.productType = s.getResource("ptype").getURI();
					msp.containsPtype = true;
				}
				if(s.getResource("platform")!=null){
					msp.platform = s.getResource("platform").getURI();
					msp.containsPlatform = true;
				}
				msp.mission = s.getResource("mission").getURI();
				missionPlatformList.add(msp);
				System.out.println("Mission Metadata : "+msp.printMsp());
			}

//			System.out.println("============================================================");
//			printParseTree1();
			System.out.println("============================================================");
			walkTreeAndMergeNodes();
			System.out.println("============================================================");
			printParseTree1();
			System.out.println("============================================================");
			String detectedPatternNew = walkTreeAndGetPattern1();
			System.out.println("++++++++++++++++++++++  Identified Pattern : " + walkTreeAndGetPattern1()
					+ "  ++++++++++++++++++++++");
			System.out.println("Postag sequance: " + postagListsInorderTree.toString());

			geoSPATIALRelations.clear();
			List<String> allSparqlQueries = new ArrayList<String>();
			boolean countFlag = false;
			int cSize = 0, rSize = 0, iSize = 0, pSize = 0,tSize = 0;
			char patterenChar[] = detectedPatternNew.toCharArray();
			for (char ch : patterenChar) {
				if (ch == 'C') {
					cSize++;
				}
				if (ch == 'R') {
					rSize++;
				}
				if (ch == 'I') {
					iSize++;
				}
				if (ch == 'P') {
					pSize++;
				}
				if (ch == 'T'){
					tSize++;
				}
			}
			if (postagListsInorderTree.get(0).contains("WRB") && postagListsInorderTree.get(1).contains("JJ")) {
				countFlag = true;

			}
			System.out.println("cSize : " + cSize + "\trSize : " + rSize + "\tiSize : " + iSize + "\tpSize : " + pSize +"\ttSize : "+ tSize
					+ "\n" + "CountFlag = " + countFlag);

			String missionTriples = create_Mission_metadata_Triples();
			String generatedSparqlQuery = " select distinct ?title ?geom where { ";
			String fixedTriple = " ?x <http://ws.eodias.eu/metadata/attribute#title> ?title . ?x <http://ws.eodias.eu/metadata/attribute#geometry> ?geom . ";
			String inspireTriple = " ?x <http://ws.eodias.eu/metadata/attribute#title> ?title . ";
			String cloudCoverageTriple = " ?x <http://ws.eodias.eu/metadata/attribute#cloudCover> ?cc . filter(?cc<20 && ?cc>=0) . ";
			int ccPercent = 10;
			boolean containsCC = false;
			String dateTriple = " ?x <http://ws.eodias.eu/metadata/attribute#startDate> ?date . ";
			String yearBindTriple = " bind(year(?date) as ?year) . ";
			String monthBindTriple = " bind(month(?date) as ?month) . ";
//			generatedSparqlQuery +=missionTriples;
			if(myQuestionNL.toLowerCase().contains("cloud cover") || myQuestionNL.toLowerCase().contains("cloud coverage")){
				containsCC = true;
				if(myQuestionNL.toLowerCase().contains("below")){
					cloudCoverageTriple = cloudCoverageTriple.replace("?cc<20","?cc<"+ccPercent);
				}
				if(myQuestionNL.toLowerCase().contains("above")){
					cloudCoverageTriple = cloudCoverageTriple.replace("?cc<20","?cc<=100");
					cloudCoverageTriple = cloudCoverageTriple.replace("?cc>=0","?cc>"+ccPercent);
				}
			}
			generatedSparqlQuery += fixedTriple + missionTriples;
			if(containsCC){
				generatedSparqlQuery += cloudCoverageTriple;
			}
			if(timeAnnotations.size()>0){

				if(timeAnnotations.size()==1){
					System.out.println("Time annotation 1 ===================");
					String dateValue = timeAnnotations.get(0).value;
					if(dateValue.contains("-")) {
						String splitted[] = dateValue.split("-");
						if(splitted.length==3){
							dateTriple += " filter( ?date > \""+dateValue+"T00:00:00\"^^<http://www.w3.org/2001/XMLSchema#dateTime> ) . ";
						}else  if(splitted.length==2){
							dateTriple += yearBindTriple + monthBindTriple + " filter(?year="+splitted[0]+" && ?month="+splitted[1]+" ) . ";
						}
						System.out.println("dateTriple : "+dateTriple);
					}
					else{

					}
				}
				if(timeAnnotations.size() == 2){
					System.out.println("Time annotation 2 ===================");
				}
				if(timeAnnotations.size()==3){
					System.out.println("Time annotation 3 ===================");
				}
			}
			if(timeAnnotations.size()>0){
				generatedSparqlQuery += dateTriple;
			}
			generatedSparqlQuery += " } LIMIT 1000 ";

			System.out.println("Generated SPARQL query : "+generatedSparqlQuery);
			concpetsLists.clear();
			relationsList.clear();
			instancesList.clear();
			postagListsInorderTree.clear();
			geoSPATIALRelations.clear();
			timeAnnotationList.clear();
			missionPlatformList.clear();
			myTreeNodes1.clear();

		}catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return myQanaryMessage;
	}
	public class Concept {
		public int begin;
		public int end;
		public String link;
		public String label;
	}

	public class SpatialRelation {
		public int index;
		public String relation;
		public String relationFunction;
	}

	public class Query {
		public int score = 0;
		public String query = "";
	}

	public class Entity {

		public int begin;
		public int end;
		public String namedEntity;
		public String uri;
		public int linkCount;

		public void print() {
			System.out.println("Start: " + begin + "\t End: " + end + "\t Entity: " + namedEntity);
		}
	}
	public class TimeAnnotation{

		public String type = ""; //TIMEX type
		public String value= ""; //TIMEX value
		public String text =""; //Annotated Text
		public int startIndex = -1;
		public int endIndex = -1;
		public String getString(){
			return "type: "+type +" value: "+value+ " text: "+text;
		}
	}
	public class Property {
		public int begin;
		public int end;
		public String label;
		public String uri;
	}
	public class MissionPlatform {
		public String mission = "";
		public String platform = "";
		public String productType = "";
		public boolean containsPlatform = false;
		public boolean containsPtype = false;

		public String printMsp(){
			return mission +" : "+platform +" : "+productType;
		}
	}
}
