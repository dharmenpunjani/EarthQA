package eu.wdaqua.qanary.hawkingdateparser;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;

import com.zoho.hawking.HawkingTimeParser;
import com.zoho.hawking.datetimeparser.configuration.HawkingConfiguration;
import com.zoho.hawking.language.english.model.DatesFound;
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
public class HawkingDateParser extends QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(HawkingDateParser.class);

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

		QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);

		HawkingTimeParser parser = new HawkingTimeParser();
		String inputText = "Good morning, Have a nice day. Shall we meet on December 20 ?";
		HawkingConfiguration hawkingConfiguration = new HawkingConfiguration();
		hawkingConfiguration.setFiscalYearStart(2);
		hawkingConfiguration.setFiscalYearEnd(1);
		hawkingConfiguration.setTimeZone("CET");
		Date referenceDate = new Date();
		DatesFound datesFound = null;
		try {
			datesFound = parser.parse(inputText, referenceDate, hawkingConfiguration, "eng"); //No I18N
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		assert datesFound != null;
		return myQanaryMessage;
	}
}
