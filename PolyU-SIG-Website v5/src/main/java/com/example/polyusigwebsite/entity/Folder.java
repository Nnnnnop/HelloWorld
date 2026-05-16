package com.example.polyusigwebsite.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import com.example.polyusigwebsite.entity.ResourceVisibility;

import java.util.List;

@Entity
@Table(name = "folder")
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Folder parent;

    @OneToMany(mappedBy = "parent")
    private List<Folder> children;

    @OneToMany(mappedBy = "folder")
    private List<ResourceFile> files;

    @Enumerated(EnumType.STRING)
    private ResourceVisibility visibility = ResourceVisibility.HIDDEN;

    public Folder() {
    }

    public Folder(String name, Folder parent) {
        this.name = name;
        this.parent = parent;
    }

    public Folder(String name, Folder parent, ResourceVisibility visibility) {
        this.name = name;
        this.parent = parent;
        this.visibility = visibility != null ? visibility : ResourceVisibility.HIDDEN;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Folder getParent() {
        return parent;
    }

    public void setParent(Folder parent) {
        this.parent = parent;
    }

    public List<Folder> getChildren() {
        return children;
    }

    public void setChildren(List<Folder> children) {
        this.children = children;
    }

    public List<ResourceFile> getFiles() {
        return files;
    }

    public void setFiles(List<ResourceFile> files) {
        this.files = files;
    }

    public ResourceVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(ResourceVisibility visibility) {
        this.visibility = visibility != null ? visibility : ResourceVisibility.HIDDEN;
    }
}