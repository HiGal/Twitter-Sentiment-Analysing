import model.word2vec
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.ml.evaluation.{BinaryClassificationEvaluator, MulticlassClassificationEvaluator}
import org.apache.spark.ml.feature.{LabeledPoint, RegexTokenizer}
import org.apache.spark.ml.tuning.{CrossValidator, ParamGridBuilder}
import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.sql.functions.regexp_replace
import org.apache.spark.sql.types.{DoubleType, IntegerType}

object TrainEval {
  def main(args: Array[String]){

    val spark = SparkSession.builder
      .master("local[*]")
      .appName("Spark CSV Reader")
      .getOrCreate
    var train_df = spark.read
      .format("csv")
      .option("header", "true")
      .load("dataset/train.csv")
    var test_df = spark.read
      .format("csv")
      .option("header", "true")
      .load("dataset/test.csv")
    train_df = train_df.withColumn("SentimentText", regexp_replace(train_df("SentimentText"), "@[a-zA-Z0-9_]+", "")).withColumnRenamed("Sentiment", "label")
//    train_df = train_df.withColumn("SentimentText", regexp_replace(train_df("SentimentText"), "&([lt][gt][amp][quot]);", " "))
    train_df = train_df.withColumn("label", train_df("label").cast(DoubleType))
    test_df = test_df.withColumn("SentimentText", regexp_replace(test_df("SentimentText"), "@[a-zA-Z0-9_]+", "")).withColumnRenamed("Sentiment", "label")
//    test_df = train_df.withColumn("SentimentText", regexp_replace(test_df("SentimentText"), "&([lt][gt][amp][quot]);", " "))


    val tokenizer = new RegexTokenizer()
      .setInputCol("SentimentText")
      .setOutputCol("Tokens")
      .setPattern("\\W+")
      .setGaps(true)

    //    val tmp = tokenizer.transform(train_df).select("Tokens").union(tokenizer.transform(test_df).select("Tokens"))
    //    val model = new word2vec().train(tmp, 50)
    //    model.save("./word2vec.model")
    val tmp = tokenizer.transform(train_df)
    val w2v = new word2vec().load_model("./word2vec.model")
    val result = w2v.transform(tmp)
    val Array(trainingData, testData) = result.randomSplit(Array(0.8, 0.2), seed = 1234L)
    var logistic =  new LogisticRegression()
//      .setMaxIter(10)
      .setFeaturesCol("result")
      .setLabelCol("label")
//      .fit(trainingData)

    val test_features = w2v.transform(tokenizer.transform(test_df))
    val paramGrid = new ParamGridBuilder()
      .addGrid(logistic.regParam, Array(0.1, 0.01, 0.3, 1))
      .addGrid(logistic.maxIter, Array(10, 20, 50, 100))
      .addGrid(logistic.elasticNetParam, Array(0, 0.1, 0.5, 0.8))
      .build()
    val cv = new CrossValidator()
      .setEstimator(logistic)
      .setEvaluator(new BinaryClassificationEvaluator)
      .setEstimatorParamMaps(paramGrid)
      .setNumFolds(5)
    val cvModel = cv.fit(trainingData)

    var predictions = cvModel.transform(testData).select("label", "SentimentText","prediction")

    predictions.printSchema()
    val evaluator = new BinaryClassificationEvaluator()
    val accuracy = evaluator.evaluate(predictions.withColumnRenamed("prediction", "rawPrediction"))
    val param = cvModel.bestModel.extractParamMap().toString()
    println(s"$param")
    println(s"Accuracy $accuracy")
//    var predictions = logistic.transform(test_features)
//    println("Predictions on test data")
//    predictions.show()
//
//    println("Predictions on validation set")
//    predictions = logistic.transform(testData)
//    predictions.show()
//
//    // Select (prediction, true label) and compute test error
//    val evaluator = new MulticlassClassificationEvaluator()
//      .setLabelCol("Sentiment")
//      .setPredictionCol("prediction")
//      .setMetricName("accuracy")
//    val accuracy = evaluator.evaluate(predictions)
//    println(s"Validation set accuracy = $accuracy")
//    //    println(s"Count 1 =$ \nCount 2 =$")
  }

}
