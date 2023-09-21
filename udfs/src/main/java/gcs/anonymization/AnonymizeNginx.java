package gcs.anonymization;

import gcs.anonymization.filters.Filters;

import io.confluent.ksql.function.udf.Udf;
import io.confluent.ksql.function.udf.UdfDescription;
import io.confluent.ksql.function.udf.UdfParameter;

import java.lang.String;

@UdfDescription(name = "anonymize_nginx",
        author = "SARAI",
        version = "0.0.1",
        description = "Functions for SARAI data anonymization.")
public class AnonymizeNginx {

    @Udf(description = "Anonymize data coming from Nginx.")
    public String anonymizeNginx(@UdfParameter String inputUnfiltered) {
        String aux = Filters.removeFieldContents(inputUnfiltered,"cookie");
        aux = Filters.removeUserNames_v2(inputUnfiltered,"src_user_name");
        aux = Filters.removeUserNames_v2(inputUnfiltered,"src_user_dn");
        aux = Filters.removeUserNames_v2(inputUnfiltered,"originsicname");
        aux = Filters.removeUserNames_v2(inputUnfiltered,"src_user_dn");

        return aux;
    }



}
