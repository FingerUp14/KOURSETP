# Курсовая работа: синхронизация файлов

Программа автоматически синхронизирует файлы двух каталогов по TCP, сохраняет конфликтующие версии и ведёт текстовый журнал. Исходный код, инструкции по запуску и сборке, тесты и отчёт находятся в каталоге [FileSynchronizer](FileSynchronizer/).

Для сборки необходимы JDK 17+ и Maven:

~~~console
cd FileSynchronizer
mvn clean package
java -jar target/FileSynchronizer-1.0.jar
~~~

Полное руководство, включая настройку двух компьютеров и запуск без графического интерфейса, приведено в [README проекта](FileSynchronizer/README.md). Отчёт и отдельная функциональная спецификация расположены в [FileSynchronizer/report](FileSynchronizer/report/).
