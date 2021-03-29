package eu.wdaqua.qanary.producttypeidentifier;

import java.util.ArrayList;

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
public class ProductTypeIdentifier extends QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(ProductTypeIdentifier.class);

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

		long startTime = System.currentTimeMillis();
		QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);
		QanaryQuestion<String> myQanaryQuestion = this.getQanaryQuestion(myQanaryMessage);
		ArrayList<String> productTypeS1List = new ArrayList<String>();
		ArrayList<String> productTypeS2List = new ArrayList<String>();
		ArrayList<String> productTypeS3List = new ArrayList<String>();
		productTypeS1List.add("GRD");
		productTypeS1List.add("SLC");
		productTypeS1List.add("RAW");
		productTypeS1List.add("OCN");
		productTypeS2List.add("L1C");
		productTypeS3List.add("EFR");
		productTypeS3List.add("ERR");
		productTypeS3List.add("WFR");
		productTypeS3List.add("WRR");
		productTypeS3List.add("LAN");
		productTypeS3List.add("LFR");
		productTypeS3List.add("LRR");
		productTypeS3List.add("LST");
		productTypeS3List.add("RBT");
		productTypeS3List.add("SRA");
		productTypeS3List.add("WAT");
		productTypeS3List.add("WST");

		// Retrieves the question string
		String myQuestion = myQanaryQuestion.getTextualRepresentation();
		logger.info("store data in graph {}", myQanaryMessage.getValues().get(myQanaryMessage.getEndpoint()));
		String missionKeyWord = "Sentinel";
		//String missionWordInquestion = "";
		/*ArrayList<String> mission = new ArrayList<String>();
		mission.add("Sentinel-1");
		mission.add("Sentinel-2");
		mission.add("Sentinel-3");*/
		/*ArrayList<String> platformList = new ArrayList<String>();
		platformList.add("S1A");
		platformList.add("S1B");
		platformList.add("S2A");
		platformList.add("S2B");
		platformList.add("S3A");*/
		/*HashMap<String, String> missionPlatform = new HashMap<String, String>();
		missionPlatform.put("Sentinel-1A","Sentinel-1");*/
		ArrayList<MissionPlatform> missionPlatforms = new ArrayList<MissionPlatform>();
		if(myQuestion.toLowerCase().contains(missionKeyWord.toLowerCase())){
			System.out.println("found mission : "+ missionKeyWord);

			if(myQuestion.toLowerCase().contains("sentinel-1")||myQuestion.toLowerCase().contains("one")){
				MissionPlatform msp = new MissionPlatform();
				msp.mission = "Sentinel-1";
				if(myQuestion.toLowerCase().contains("1a")||myQuestion.toLowerCase().contains("one a ")){
					msp.platform = "S1A";
					msp.containsPlatform = true;
				}
				if(myQuestion.toLowerCase().contains("1b")||myQuestion.toLowerCase().contains("one b ")){
					msp.platform = "S1B";
					msp.containsPlatform = true;
				}
				for( String ptype: productTypeS1List){
					if(myQuestion.toLowerCase().contains(ptype.toLowerCase())){
						msp.productType = ptype;
						msp.containsPtype = true;
					}
				}
				if(!msp.containsPtype){
					if(myQuestion.toLowerCase().contains("ground range detected")){
						msp.productType = "GRD";
						msp.containsPtype = true;
					} else if(myQuestion.toLowerCase().contains("single look complex")){
						msp.productType = "SLC";
						msp.containsPtype = true;
					} else if(myQuestion.toLowerCase().contains("ocean")){
						msp.productType = "OCN";
						msp.containsPtype = true;
					}
				}
				missionPlatforms.add(msp);
			}
			if(myQuestion.toLowerCase().contains("sentinel-2")||myQuestion.toLowerCase().contains("two")){
				MissionPlatform msp = new MissionPlatform();
				msp.mission = "Sentinel-2";
				if(myQuestion.toLowerCase().contains("2a")||myQuestion.toLowerCase().contains("two a ")){
					msp.platform = "S2A";
					msp.containsPlatform = true;
				}
				if(myQuestion.toLowerCase().contains("2b")||myQuestion.toLowerCase().contains("two b ")){
					msp.platform = "S2B";
					msp.containsPlatform = true;
				}
				for( String ptype: productTypeS2List){
					if(myQuestion.toLowerCase().contains(ptype.toLowerCase())){
						msp.productType = ptype;
						msp.containsPtype = true;
					}
				}
				missionPlatforms.add(msp);
			}
			if(myQuestion.toLowerCase().contains("sentinel-3")||myQuestion.toLowerCase().contains("three")){
				MissionPlatform msp = new MissionPlatform();
				msp.mission = "Sentinel-3";
				if(myQuestion.toLowerCase().contains("3a")||myQuestion.toLowerCase().contains("three a ")){
					msp.platform = "S3A";
					msp.containsPlatform = true;
				}
				for( String ptype: productTypeS3List){
					if(myQuestion.toLowerCase().contains(ptype.toLowerCase())){
						msp.productType = ptype;
						msp.containsPtype = true;
					}
				}
				if(!msp.containsPtype){
					if(myQuestion.toLowerCase().contains("eo processing mode for full resolution")){
						msp.productType = "EFR";
						msp.containsPtype = true;
					} else if(myQuestion.toLowerCase().contains("eo processing mode for reduced resolution")){
						msp.productType = "ERR";
						msp.containsPtype = true;
					} else if(myQuestion.toLowerCase().contains("water full resolution")){
						msp.productType = "WFR";
						msp.containsPtype = true;
					} else if(myQuestion.toLowerCase().contains("water reduced resolution")){
						msp.productType = "WRR";
						msp.containsPtype = true;
					} else if(myQuestion.toLowerCase().contains("land product")){
						msp.productType = "LAN";
						msp.containsPtype = true;
					} else if(myQuestion.toLowerCase().contains("land full resolution")){
						msp.productType = "LFR";
						msp.containsPtype = true;
					} else if(myQuestion.toLowerCase().contains("land reduced resolution")){
						msp.productType = "LRR";
						msp.containsPtype = true;
					} else if(myQuestion.toLowerCase().contains("land surface temperature")){
						msp.productType = "LST";
						msp.containsPtype = true;
					} else if(myQuestion.toLowerCase().contains("radiance and brightness temperature")){
						msp.productType = "RBT";
						msp.containsPtype = true;
					} else if(myQuestion.toLowerCase().contains("water product")){
						msp.productType = "WAT";
						msp.containsPtype = true;
					} else if(myQuestion.toLowerCase().contains("water single temperature")){
						msp.productType = "WST";
						msp.containsPtype = true;
					}
				}
				missionPlatforms.add(msp);
			}
		} else {
			MissionPlatform msp = new MissionPlatform();
			for( String ptype: productTypeS1List){
				if(myQuestion.toLowerCase().contains(ptype.toLowerCase())){
					msp.productType = ptype;
					msp.containsPtype = true;
					msp.mission = "Sentinel-1";
				}
			}
			if(!msp.containsPtype){
				if(myQuestion.toLowerCase().contains("ground range detected")){
					msp.productType = "GRD";
					msp.containsPtype = true;
				} else if(myQuestion.toLowerCase().contains("single look complex")){
					msp.productType = "SLC";
					msp.containsPtype = true;
				} else if(myQuestion.toLowerCase().contains("ocean")){
					msp.productType = "OCN";
					msp.containsPtype = true;
				}
				if(msp.containsPtype){
					msp.mission = "Sentinel-1";
				}
			}
			if(msp.containsPtype) {
				missionPlatforms.add(msp);
			}

			MissionPlatform msp2 = new MissionPlatform();
			for( String ptype: productTypeS2List){
				if(myQuestion.toLowerCase().contains(ptype.toLowerCase())){
					msp2.productType = ptype;
					msp2.containsPtype = true;
					msp2.mission = "Sentinel-2";
				}
			}
			if(msp2.containsPtype) {
				missionPlatforms.add(msp2);
			}

			MissionPlatform msp3 = new MissionPlatform();
			for( String ptype: productTypeS3List){
				if(myQuestion.toLowerCase().contains(ptype.toLowerCase())){
					msp3.productType = ptype;
					msp3.containsPtype = true;
					msp3.mission = "Sentinel-3";
				}
			}

			if(!msp3.containsPtype){
				/*Pattern p = Pattern.compile("\\b" + enrty.getKey() + "\\b", Pattern.CASE_INSENSITIVE);
				Matcher m = p.matcher(verb);
				if(m.find()){

				}*/
				if(myQuestion.toLowerCase().contains("eo processing mode for full resolution")){
					msp3.productType = "EFR";
					msp3.containsPtype = true;
				} else if(myQuestion.toLowerCase().contains("eo processing mode for reduced resolution")){
					msp3.productType = "ERR";
					msp3.containsPtype = true;
				} else if(myQuestion.toLowerCase().contains("water full resolution")){
					msp3.productType = "WFR";
					msp3.containsPtype = true;
				} else if(myQuestion.toLowerCase().contains("water reduced resolution")){
					msp3.productType = "WRR";
					msp3.containsPtype = true;
				} else if(myQuestion.toLowerCase().contains("land product")){
					msp3.productType = "LAN";
					msp3.containsPtype = true;
				} else if(myQuestion.toLowerCase().contains("land full resolution")){
					msp3.productType = "LFR";
					msp3.containsPtype = true;
				} else if(myQuestion.toLowerCase().contains("land reduced resolution")){
					msp3.productType = "LRR";
					msp3.containsPtype = true;
				} else if(myQuestion.toLowerCase().contains("land surface temperature")){
					msp3.productType = "LST";
					msp3.containsPtype = true;
				} else if(myQuestion.toLowerCase().contains("radiance and brightness temperature")){
					msp3.productType = "RBT";
					msp3.containsPtype = true;
				} else if(myQuestion.toLowerCase().contains("water product")){
					msp3.productType = "WAT";
					msp3.containsPtype = true;
				} else if(myQuestion.toLowerCase().contains("water single temperature")){
					msp3.productType = "WST";
					msp3.containsPtype = true;
				}
				if(msp3.containsPtype){
					msp3.mission = "Sentinel-3";
				}
			}
			if(msp3.containsPtype) {
				missionPlatforms.add(msp3);
			}
		}
		String sparql = "";
		for(MissionPlatform msp : missionPlatforms){
			if(msp.containsPlatform){
				if(msp.containsPtype){
					sparql = "" //
							+ "prefix qa: <http://www.wdaqua.eu/qa#> "
							+ "prefix oa: <http://www.w3.org/ns/openannotation/core/> "
							+ "prefix xsd: <http://www.w3.org/2001/XMLSchema#> "
							+ "INSERT { "
							+ "GRAPH <"	+ myQanaryQuestion.getOutGraph() + "> { "
							+ "  ?a a qa:AnnotationOfMissionMetadata . "
							+ "  ?a oa:hasMission <http://ws.eodias.eu/metadata/mission/" + msp.mission + "> . "
							+ "	 ?a oa:hasPlatform <http://ws.eodias.eu/metadata/platform/" + msp.platform + "> . "
							+ "  ?a oa:hasProductType <http://ws.eodias.eu/metadata/productType/"+msp.productType+">."
							+ "} } "
							+ "WHERE { " //
							+ "  BIND (IRI(str(RAND())) AS ?a) ."//
							+ "}"	;

				} else {
					sparql = "" //
							+ "prefix qa: <http://www.wdaqua.eu/qa#> "
							+ "prefix oa: <http://www.w3.org/ns/openannotation/core/> "
							+ "prefix xsd: <http://www.w3.org/2001/XMLSchema#> "
							+ "INSERT { "
							+ "GRAPH <"	+ myQanaryQuestion.getOutGraph() + "> { "
							+ "  ?a a qa:AnnotationOfMissionMetadata . "
							+ "  ?a oa:hasMission <http://ws.eodias.eu/metadata/mission/" + msp.mission + "> . "
							+ "	 ?a oa:hasPlatform <http://ws.eodias.eu/metadata/platform/" + msp.platform + "> . "
							+ "} } "
							+ "WHERE { " //
							+ "  BIND (IRI(str(RAND())) AS ?a) ."//
							+ "}"	;
				}
			} else if(msp.containsPtype){
				sparql = "" //
						+ "prefix qa: <http://www.wdaqua.eu/qa#> "
						+ "prefix oa: <http://www.w3.org/ns/openannotation/core/> "
						+ "prefix xsd: <http://www.w3.org/2001/XMLSchema#> "
						+ "INSERT { "
						+ "GRAPH <"	+ myQanaryQuestion.getOutGraph() + "> { "
						+ "  ?a a qa:AnnotationOfMissionMetadata . "
						+ "  ?a oa:hasMission <http://ws.eodias.eu/metadata/mission/" + msp.mission + "> . "
						+ "  ?a oa:hasProductType <http://ws.eodias.eu/metadata/productType/"+msp.productType+">."
						+ "} } "
						+ "WHERE { " //
						+ "  BIND (IRI(str(RAND())) AS ?a) ."//
						+ "}"	;
			} else if(msp.mission!=""){
				sparql = "" //
						+ "prefix qa: <http://www.wdaqua.eu/qa#> "
						+ "prefix oa: <http://www.w3.org/ns/openannotation/core/> "
						+ "prefix xsd: <http://www.w3.org/2001/XMLSchema#> "
						+ "INSERT { "
						+ "GRAPH <"	+ myQanaryQuestion.getOutGraph() + "> { "
						+ "  ?a a qa:AnnotationOfMissionMetadata . "
						+ "  ?a oa:hasMission <http://ws.eodias.eu/metadata/mission/" + msp.mission + "> . "
						+ "} } "
						+ "WHERE { " //
						+ "  BIND (IRI(str(RAND())) AS ?a) ."//
						+ "}"	;
			}
			if(sparql!="")
				myQanaryUtils.updateTripleStore(sparql,myQanaryUtils.getEndpoint());
		}
		return myQanaryMessage;
	}
	public class MissionPlatform {
		public String mission = "";
		public String platform = "";
		public String productType = "";
		public boolean containsPlatform = false;
		public boolean containsPtype = false;
	}
}
