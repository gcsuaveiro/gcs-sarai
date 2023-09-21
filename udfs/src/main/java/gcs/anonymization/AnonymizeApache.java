package gcs.anonymization;

import gcs.anonymization.filters.Filters;

import io.confluent.ksql.function.udf.Udf;
import io.confluent.ksql.function.udf.UdfDescription;
import io.confluent.ksql.function.udf.UdfParameter;

import java.lang.String;
import java.util.logging.Filter;

@UdfDescription(name = "anonymize_apache",
        author = "SARAI",
        version = "0.0.1",
        description = "Functions for SARAI data anonymization.")
public class AnonymizeApache {

    @Udf(description = "Anonymize data coming from apache.")
    public String anonymizeApache(@UdfParameter String inputUnfiltered) {
        String aux = Filters.apache_SAMLFilter(inputUnfiltered);
        aux = Filters.apache_removeFieldContents(aux,"sesskey");
        aux = Filters.apache_removeFieldContents(aux,"cachekey");
        aux = Filters.apache_removeFieldContents(aux,"email");

        return aux;
    }



}
