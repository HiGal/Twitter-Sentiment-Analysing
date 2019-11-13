package model

import org.apache.spark.ml.feature.{RegexTokenizer, Tokenizer, Word2Vec}
import org.apache.spark.sql.{SQLContext, SparkSession}

object word2vec {
  def main(args: Array[String]): Unit = {
    //    val conf = new SparkConf().setAppName("Word2vec")
    //    val sc = new SparkContext(conf)
    val spark = SparkSession.builder
      .master("local")
      .appName("Spark CSV Reader")
      .getOrCreate

    val df = spark.read
      .format("csv")
      .option("header", "true") //first line in file has headers
      .load("dataset/train.csv")
    import spark.implicits._
    val model = new Word2Vec()
    model.setMaxIter(5)
    model.setVectorSize(128)
    model.setInputCol("Tokens")
    model.setOutputCol("result")

    val tokenizer = new RegexTokenizer()
      .setInputCol("SentimentText")
      .setOutputCol("Tokens")
      .setPattern("\\W+")
      .setGaps(true)


    val tmp = tokenizer.transform(df)
    tmp.show(10)
    val w2vmodel = model.fit(tmp)
    val result = w2vmodel.transform(tmp)
    result.select("result").take(3).foreach(println)
    val ds = w2vmodel.findSynonyms("apple", 5).select("word")
    ds.show(5)
  }

}
