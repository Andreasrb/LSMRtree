package rtree.utilities


import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import rtree.base.DataObject
import java.io.File

class ImportRealData(private val filePath: String) {

    /**
     * Designed to use Porto taxi trips dataset on kaggle.
     * Contains polylines in the last column, being a list of coordinate pairs for each taxi trip.
     */
    fun readPolylines(numLines: Int): List<List<Pair<Double, Double>>> {
        val allCoordinates = mutableListOf<List<Pair<Double, Double>>>()

        return try {
            BufferedReader(FileReader(filePath)).use { br ->
                repeat(numLines) {
                    val line = br.readLine()
                    if (line != null) {
                        val parts = line.split(",(?=\")".toRegex()) // Split only at top-level commas, preserving quoted values
                        if (parts.isNotEmpty()) {
                            val coordinatesJson = parts.last().trim().removeSurrounding("\"") // Extract last column and remove extra quotes

                            if (coordinatesJson.startsWith("[[") && coordinatesJson.endsWith("]]")) {
                                try {
                                    val coordinateList = Json.decodeFromString<List<List<Double>>>(coordinatesJson)
                                    val parsedCoordinates = coordinateList.map { Pair(it[0], it[1]) }
                                    allCoordinates.add(parsedCoordinates) // Store all coordinate pairs from this row
                                } catch (e: SerializationException) {
                                    e.printStackTrace() // Handle JSON parsing errors
                                }
                            }
                        }
                    } else {
                        return@use allCoordinates // Stop if EOF is reached
                    }
                }
                allCoordinates
            }
        } catch (e: IOException) {
            e.printStackTrace()
            emptyList() // Return empty list if an error occurs
        }
    }

    fun exportCoordinates(polylines: List<List<Pair<Double, Double>>>, outputPath: String) {
        var numCoords = 0
        try {
            File(outputPath).bufferedWriter().use { writer ->
                polylines.forEach { coordinates ->
                    coordinates.forEach { (x, y) ->
                        numCoords++
                        writer.write("$x,$y\n") // Write each coordinate pair to a new line
                    }
                }
            }
            println("Exported ${numCoords} coordinates to $outputPath")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun convertRawData(path: String, number: Int): ArrayList<DataObject> {
        val lines = readPolylines(number)
        val dataObjects: ArrayList<DataObject> = ArrayList()

        for (polyLines in lines) {
            for (line in polyLines) {
                val x1 = line.first
                val y1 = line.second
                val x2 = line.first
                val y2 = line.second
                dataObjects.add(DataObject(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat()))
            }
        }
        return dataObjects
    }
}
