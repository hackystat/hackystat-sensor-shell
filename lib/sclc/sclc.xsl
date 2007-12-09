<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="xml" indent="yes"/>

<xsl:template match="/">
  <XmlData>
    <Entries>
      <xsl:apply-templates select="sclc/file"/>  
    </Entries>
  </XmlData>
</xsl:template>

<xsl:template match="file">
    <Entry SensorDataType="FileMetric" Tool="SCLC" Resource="{@fileName}" FileType="{@fileType}" 
        TotalLines="{@totalLines}" BlankLines="{@blankLines}" CommentLines="{@commentLines}" SourceLines="{@sourceLines}" />
</xsl:template>

</xsl:stylesheet>