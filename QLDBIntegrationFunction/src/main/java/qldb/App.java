package qldb;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonReaderBuilder;
import com.amazon.ion.system.IonSystemBuilder;
import com.amazon.ion.system.IonTextWriterBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.qldbsession.AmazonQLDBSessionClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;
import com.fasterxml.jackson.dataformat.ion.ionvalue.IonValueMapper;
import io.burt.jmespath.Expression;
import io.burt.jmespath.JmesPath;
import io.burt.jmespath.jackson.JacksonRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.qldb.*;
import software.amazon.qldb.exceptions.QldbClientException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<IntegrationRequest, Object> {

    private static PooledQldbDriver DRIVER;
    private static final IonObjectMapper MAPPER;
    private static Logger LOGGER;

    static {
        DRIVER = createQLDBDriver();
        MAPPER = new IonValueMapper(IonSystemBuilder.standard().build());
        LOGGER = LoggerFactory.getLogger(App.class);
    }

    /**
     * Main request handler for Lambda function.
     * @param input
     * @param context
     * @return
     */
    public Object handleRequest(final IntegrationRequest input, final Context context) {
        IntegrationResponse response = new IntegrationResponse();

        try {
            String result = executeTransaction(input.getPayload());
            response.setResult(result);
            response.setSuccess(true);
        } catch (Exception e) {
            LOGGER.error("[ERROR] {}", e.getMessage());
            response.setError(e.getMessage());
        }

        return response;
    }


    /**
     *
     * @param queries
     */
    private String executeTransaction(List<Query> queries) {
        try (QldbSession qldbSession = createQldbSession()) {
            List<String> result = new ArrayList<String>();

            qldbSession.execute((ExecutorNoReturn) txn -> {
                for (Query q : queries) {
                    LOGGER.info("Executing query: {}", q.query);
                    String lastResult = result.size() > 0 ? result.get(result.size() - 1) : "";
                    result.add(executeQuery(txn, q, lastResult));
                }
            }, (retryAttempt) -> LOGGER.info("Retrying due to OCC conflict..."));

            return result.get(result.size() - 1);
        } catch (QldbClientException e) {
            LOGGER.error("Unable to create QLDB session: {}", e.getMessage());
        }

        return "{}";
    }

    /**
     *
     * @param txn
     * @param query
     * @param lastResult
     * @return
     */
    private String executeQuery(final TransactionExecutor txn, final Query query, final String lastResult) {
        final List<IonValue> params = new ArrayList<IonValue>();
        query.getArgs().forEach((a) -> {
            LOGGER.debug("Adding arg {} to query", a);
            try {
                String arg = a.startsWith("$.") && !lastResult.isEmpty() ?
                        queryWithJmesPath(lastResult, a.substring(2)) : a;
                params.add(MAPPER.writeValueAsIonValue(arg));
            } catch (IOException e) {
                LOGGER.error("Could not write value as Ion: {}", a);
                LOGGER.error("[ERROR] {}", e.getMessage());
            }
        });

        // Execute the query and transform response to JSON string...
        List<String> json = new ArrayList<String>();
        txn.execute(query.getQuery(), params).iterator().forEachRemaining(r -> {
            String j = convertToJson(r.toPrettyString());
            json.add(j);
        });

        return json.toString();
    }

    /**
     *
     * @param ionText
     * @return
     */
    private String convertToJson(String ionText) {
        StringBuilder builder = new StringBuilder();
        try (IonWriter jsonWriter = IonTextWriterBuilder.json().withPrettyPrinting().build(builder)) {
            IonReader reader = IonReaderBuilder.standard().build(ionText);
            jsonWriter.writeValues(reader);
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
        return builder.toString();
    }

    private String queryWithJmesPath(String json, String jmesExpression)
            throws IOException {
        LOGGER.debug("Query with JMESPath: {} on {}", jmesExpression, json);

        JmesPath<JsonNode> jmespath = new JacksonRuntime();
        Expression<JsonNode> expression = jmespath.compile(jmesExpression);

        ObjectMapper om = new ObjectMapper();
        JsonNode input = om.readTree(json);
        // TODO: handling quotes this way is a bit yucky and may be problematic elsewhere...
        String result = om.writeValueAsString(expression.search(input)).replaceAll("^\"|\"$", "");

        LOGGER.info("JMESPath query returned: {}", result);

        return result;
    }


    /**
     * Returns a QLDB session.
     * @return
     */
    private QldbSession createQldbSession() {
        return DRIVER.getSession();
    }

    /**
     * Creates a pooled QLDB driver that enables connection to the service.
     * @return
     */
    private static PooledQldbDriver createQLDBDriver() {
        AmazonQLDBSessionClientBuilder builder = AmazonQLDBSessionClientBuilder.standard();

        return PooledQldbDriver.builder()
                .withLedger(System.getenv("QLDB_LEDGER"))
                .withSessionClientBuilder(builder)
                .build();
    }
}
