package models

case class AnalysisTree(
  indexId:      Int,
  file:         String,
  analysisType: AnalysisType) {
  def analysisPath(base: String) = analysisType.path(base, file)
}
