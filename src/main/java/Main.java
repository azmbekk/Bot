import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

import java.util.Scanner;

public class Main {

    public static void main(String[] arg) {
        final ActorSystem system = ActorSystem.create("Bot");      //Инициализация системы акторов
        final ActorRef httpActor = system.actorOf(Props.create(HttpActor.class), "HttpActor");     //Получаем ссылку на HttpActor
        final Scanner scanner = new Scanner(System.in);

        System.out.println("please, ask your question.");

        boolean isNext = false;

        while (true) {
            String line = scanner.nextLine();
            //Чекпойнты для вводимых данных

            if (line.isEmpty()) {
                System.out.println("The question cannot be empty.");
                continue;
            }
            if (line.length() > 200) {
                System.out.println("The question is too big.");
                continue;
            }
            if (line.equals("exit") || (line.equalsIgnoreCase("N") && isNext)) {
                system.terminate();
                break;
            }
            if (line.equalsIgnoreCase("Y") && isNext) {
                continue;
            }

            isNext = true;

            //Отправка сообщения
            httpActor.tell(line, ActorRef.noSender());
        }
    }
}
