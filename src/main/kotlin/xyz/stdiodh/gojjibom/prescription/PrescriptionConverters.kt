package xyz.stdiodh.gojjibom.prescription

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = false)
class DoseBasisConverter : AttributeConverter<DoseBasis, String> {
    override fun convertToDatabaseColumn(attribute: DoseBasis): String = attribute.name.lowercase()

    override fun convertToEntityAttribute(dbData: String): DoseBasis = DoseBasis.valueOf(dbData.uppercase())
}

@Converter(autoApply = false)
class DispensingTypeConverter : AttributeConverter<DispensingType, String> {
    override fun convertToDatabaseColumn(attribute: DispensingType): String = attribute.name.lowercase()

    override fun convertToEntityAttribute(dbData: String): DispensingType = DispensingType.valueOf(dbData.uppercase())
}

@Converter(autoApply = false)
class PillShapeConverter : AttributeConverter<PillShape, String> {
    override fun convertToDatabaseColumn(attribute: PillShape): String = attribute.name.lowercase()

    override fun convertToEntityAttribute(dbData: String): PillShape = PillShape.valueOf(dbData.uppercase())
}
