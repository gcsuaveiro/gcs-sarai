package gcs.anonymization;

import gcs.anonymization.filters.Filters;
import io.confluent.ksql.function.udf.Udf;
import io.confluent.ksql.function.udf.UdfDescription;
import io.confluent.ksql.function.udf.UdfParameter;

@UdfDescription(name = "anonymize_checkpoint",
        author = "SARAI",
        version = "0.0.1",
        description = "Functions for SARAI data anonymization.")
public class AnonymizeCheckpointUdf {

    @Udf(description = "Anonymize data coming from the Firewall.")
    public String anonymizeCheckpoint(@UdfParameter String inputUnfiltered) {
        String aux = "";
        aux = Filters.removeUserNames_v2(inputUnfiltered,"usrName");
        aux = Filters.removeUserNames_v2(aux,"src_user_name");
        aux = Filters.removeUserNames_v2(aux,"src_user_dn");
        aux = Filters.removeUserNames_v2(aux,"originsicname");
        return aux;
    }

}
