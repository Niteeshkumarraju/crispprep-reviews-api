package com.crispprep.reviews.entity;
import jakarta.persistence.*;
@Entity
public class Pdf {
    
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false)
    private String title;
    private String subject;
protected Pdf() { }
public Pdf(String title, String subject){
    this.title = title;
    this.subject=subject;
}
 public Long getId(){
    return id;}
    public String getTitle(){return title;}
    public String getSubject(){return subject;}
} 
