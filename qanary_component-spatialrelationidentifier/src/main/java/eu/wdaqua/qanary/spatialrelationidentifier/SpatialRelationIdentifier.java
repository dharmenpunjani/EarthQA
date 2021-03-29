package eu.wdaqua.qanary.spatialrelationidentifier;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

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
public class SpatialRelationIdentifier extends QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(SpatialRelationIdentifier.class);

	public RelationDetector myRelationDetector=new RelationDetector();
	/**
	 * implement this method encapsulating the functionality of your Qanary
	 * component, some helping notes w.r.t. the typical 3 steps of implementing a
	 * Qanary component are included in the method (you might remove all of them)
	 * 
	 * @throws SparqlQueryFailed
	 */
	@Override
	public QanaryMessage process(QanaryMessage myQanaryMessage) throws Exception {
		long startTime = System.currentTimeMillis();
		logger.info("process: {}", myQanaryMessage);

		try {
			logger.info("store data in graph {}", myQanaryMessage.getValues().get(new URL(myQanaryMessage.getEndpoint().toString())));
			QanaryQuestion<String> myQanaryQuestion = this.getQanaryQuestion(myQanaryMessage);
			QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);

			// compute and evaluate the GeoRelations
			List<RelationDetectorAnswer> myDetectedRelationList = this.myRelationDetector.process(myQanaryQuestion);

			// store the data in the provided Qanary triplestore using the
			// defined outgraph
			if (myDetectedRelationList != null) {


				for(RelationDetectorAnswer myDetectedRelation : myDetectedRelationList){
					// Push the GeoRelation in graph
					logger.info("applying vocabulary alignment on outgraph {}", myQanaryQuestion.getOutGraph());
					String sparql = "" //
							+ "PREFIX qa: <http://www.wdaqua.eu/qa#> " //
							+ "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> " //
							+ "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> " //
							+ "INSERT { " //
							+ "GRAPH <" + myQanaryQuestion.getOutGraph() + "> { " //
							+ "  ?a a qa:AnnotationOfRelation." //
							+ "  ?a oa:hasTarget [ " //
							+ "        a    oa:SpecificResource; " //
							+ "        oa:hasSource    <" + myQanaryQuestion.getUri() + ">; " //
							+ "        oa:hasRelation [ " //
							+ "          a oa:GeoRelation ; " //
							+ "          oa:geoRelation <" + myDetectedRelation.getGeospatialRelationIdentifier() + "> ; " //
							+ "             oa:hasSelector  [ " //
							+ "                    a oa:TextPositionSelector ; " //
							+ "                    oa:start \"" + myDetectedRelation.getIndexBegin() + "\"^^xsd:nonNegativeInteger ; " //
							+ "                    oa:relString \"" +myDetectedRelation.relationStringInQuestion +"\"^^xsd:string ;"
							+ "             ] " //
							+ "        ] " //
							+ "  ] " + "}} " //
							+ "WHERE { " //
							+ "BIND (IRI(str(RAND())) AS ?a) ." //
							+ "BIND (now() as ?time) " //
							+ "}";
					myQanaryUtils.updateTripleStore(sparql, myQanaryQuestion.getEndpoint().toString());
				}
			}

			// used time for processing
			logger.info("Time {}", System.currentTimeMillis() - startTime);

		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return myQanaryMessage;
	}
}
