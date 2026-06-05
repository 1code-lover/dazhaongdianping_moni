package com.hmdp.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Document(indexName = "shop")
public class ShopDocument {
    @Id
    private Long id;
    
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String name;
    
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String address;
    
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String area;
    
    @Field(type = FieldType.Keyword)
    private String images;
    
    @Field(type = FieldType.Integer)
    private Integer score;
    
    @Field(type = FieldType.Long)
    private Long avgPrice;
    
    @Field(type = FieldType.Double)
    private Double x;
    
    @Field(type = FieldType.Double)
    private Double y;
}
