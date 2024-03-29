import org.apache.spark.ml.classification.{LinearSVC, LogisticRegression, MultilayerPerceptronClassifier, RandomForestClassifier}
import org.apache.spark.ml.feature.{Normalizer, RegexTokenizer, StopWordsRemover, Word2Vec}
import org.apache.spark.ml.{Pipeline, PipelineModel}
import org.apache.spark.mllib.evaluation.MulticlassMetrics
import org.apache.spark.sql
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.DoubleType
import utilities.Cleaner


object TrainModels {
  // Evaluate predictions of the model
  def evaluate(predictions: sql.DataFrame): Unit ={
    // Take prediction and label and transform to rdd
    val labels = predictions.select("prediction", "label").rdd.map(r => (r.getDouble(0), r.getDouble(1)))
    // Compute metrics
    val metrics = new MulticlassMetrics(labels)
    val accuracy= metrics.accuracy
    val f1_score = metrics.fMeasure(0)
    // Output metrics
    println(s"Validation set accuracy = $accuracy")
    println(s"F1 score = $f1_score")
  }
  def main(args: Array[String]) {
    val usage = "Usage: program <dataset_folder> <model_name>" +
      "\n" +
      "\n" +
      "Possible models:\n" +
      "All require dataset train.csv in the dataset directory\n" +
      "\tword2vec - word2vec model\n" +
      "All those below also require a pipeline pretrained model\n" +
      "\tlogistic - logistic regression\n" +
      "\tperceptron - multilayer perceptron model\n" +
      "\tsvm - linear SVC model\n" +
      "\tforest - Random Forest model\n"
    if (args.length != 2) {
      println(usage)
      System.exit(0)
    }
    // Get arguments
    val data_folder = args(0)
    val model_name = args(1)
    // Create spark Session
    val spark = SparkSession.builder
      .appName("Spark CSV Reader")
      .getOrCreate

    // Load training dataset
    val train_df = spark.read
      .format("csv")
      .option("header", "true")
      .load(s"$data_folder/train.csv")
    // Init tokenizer transformer
    val tokenizer = new RegexTokenizer()
      .setInputCol("SentimentText")
      .setOutputCol("Tokens")
      .setPattern("\\W+")
      .setGaps(true)

    // Init stop words remover transformer (english by default)
    val remover = new StopWordsRemover()
      .setInputCol("Tokens")
      .setOutputCol("filteredPhrases")

    // Ini cleaner transformer that will remove nickname tags and html tags
    val cleaner = new Cleaner()
      .setInputCol("SentimentText")
      .setOutputCol("SentimentText")
      .setRegularExpressions(Array("@[a-zA-Z0-9_]+", "&(lt)?(gt)?(amp)?(quot)?;"))

    // Normalizer model
    val normalizer = new Normalizer()
      .setInputCol("word2vec")
      .setOutputCol("normedW2V")


    // If training the word2vec - we are training a feature extraction pipeline
    if (model_name.equals("word2vec")) {
      // Load test dataset
      val test_df = spark.read
        .format("csv")
        .option("header", "true")
        .load(s"$data_folder/test.csv")

      // Create new word2vec model
      val model = new Word2Vec()
        .setMaxIter(50)
        .setVectorSize(128)
        .setInputCol("Tokens")
        .setOutputCol("word2vec")
      // Init pipeline
      val preprocessPipeline = new Pipeline().setStages(Array(cleaner, tokenizer, remover, model, normalizer))
      val result = preprocessPipeline.fit(train_df.select("SentimentText").union(test_df.select("SentimentText")))
      // Save trained word2vec
      result.save("./pipeline")
      // Else we are training models with pretrained pipeline
    } else {
      val pipeline = PipelineModel.load("./pipeline")
      val features = pipeline.transform(train_df)
      val Array(trainingData, testData) = features.withColumn("label", features("Sentiment").cast(DoubleType)).randomSplit(Array(0.8, 0.2), seed = 1234L)

      if (model_name == "logistic") {
        // Init logistic
        val model = new LogisticRegression()
          .setMaxIter(50)
          .setRegParam(0)
          .setElasticNetParam(0)
          .setFeaturesCol("normedW2V")
          .setLabelCol("label")
          .fit(trainingData)
        // Save, predict and evaluate model
        model.save("./logistic.model")
        val predictions = model.transform(testData)
        evaluate(predictions)
      } else if (model_name == "perceptron"){
        // Define perceptron layers
        val layers = Array[Int](128, 64, 32, 2)

        // Init perceptron model
        val model = new MultilayerPerceptronClassifier()
          .setLayers(layers)
          .setBlockSize(64)
          .setSeed(1234L)
          .setMaxIter(100)
          .setFeaturesCol("normedW2V")
          .setLabelCol("label")
          .fit(trainingData)
        // Save, predict and evaluate model
        model.save("./perceptron.model")
        val predictions = model.transform(testData)
        evaluate(predictions)
      } else if (model_name == "svm"){
        // Init SVM model
        val model = new LinearSVC()
          .setMaxIter(100)
          .setRegParam(0)
          .setFeaturesCol("normedW2V")
          .setLabelCol("label")
          .fit(trainingData)
        // Save, predict and evaluate model
        model.save("./svm.model")
        val predictions = model.transform(testData)
        evaluate(predictions)
      } else if (model_name == "forest"){
        // Init Random Forest
        val model = new RandomForestClassifier()
          .setNumTrees(100)
          .setFeaturesCol("normedW2V")
          .setLabelCol("label")
          .fit(trainingData)
        // Save, predict and evaluate model
        model.save("./forest.model")
        val predictions = model.transform(testData)
        evaluate(predictions)
      } else{
        println(usage)
        System.exit(0)
      }
    }
  }
}
