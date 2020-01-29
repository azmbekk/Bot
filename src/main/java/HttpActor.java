import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.stream.Materializer;
import akka.util.ByteString;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import scala.concurrent.ExecutionContextExecutor;

import java.util.concurrent.CompletionStage;

import static akka.pattern.Patterns.pipe;

public class HttpActor extends AbstractActor {
    private static final String URL = "https://odqa.demos.ivoice.online/model";

    private final LoggingAdapter logger = Logging.getLogger(getContext().getSystem(), this);
    private final Http http = Http.get(context().system());
    private final ExecutionContextExecutor dispatcher = getContext().getDispatcher();
    private final Materializer materializer = Materializer.createMaterializer(context().system());

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                //https://doc.akka.io/docs/akka-http/current/client-side/request-level.html#using-the-future-based-api-in-actors
                //Согласно документации, не рекомендуется использовать колбеки для CompletionState.
                //Вместо этого рекомендуется использовать pipe корректной обработки фьючерсов.
                //Здесь после совершения HTTP запроса, ответ от сервера посылается самому себе и обрабатывается
                //в handleResponse
                .match(String.class, s -> pipe(makeRequest(s), dispatcher).to(self()))
                .match(HttpResponse.class, this::handleResponse)
                .matchAny(o -> logger.info("Received unhandled message."))
                .build();
    }


    //Создание Http POST запроса
    private CompletionStage<HttpResponse> makeRequest(String question) {
        JSONArray jsonArray = new JSONArray();
        jsonArray.put(question);
        JSONObject json = new JSONObject();
        json.put("context", jsonArray);

        logger.debug(json.toString());

        HttpRequest request = HttpRequest.POST(URL).withEntity(ContentTypes.APPLICATION_JSON, json.toString());
        return http.singleRequest(request);
    }


    //Парсим JSON
    //Ответ от сервера состоит из трех вложенных JSON массивов.
    private String parseJSON(String str) {
        String result = "";
        try {
            JSONArray array0 = new JSONArray(str);
            JSONArray array1 = array0.getJSONArray(0);
            JSONArray array2 = array1.getJSONArray(0);

            result = array2.get(0).toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    //Обработка ответа
    private void handleResponse(HttpResponse response) {
        if (response.status() == StatusCodes.OK) {
            response.entity().getDataBytes()        //Извлекаем данные в формате Source<ByteString, Object>
                    .map(ByteString::utf8String)    //Приводим Source<ByteString, Object> в Source<String, Object> для удобного использования
                    .map(this::parseJSON)           //Парсим строку
                    .runForeach(string -> {         //Выводим строку в консоль.
                        System.out.println(string);
                        System.out.print("Ask next question? Y/n ");
                    }, materializer);
        } else {
            logger.error("Server unreachable");
        }
    }
}
