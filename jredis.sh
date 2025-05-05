set -e

#mvn clean package

exec java -jar target/JRedis-1.0.jar "$@"