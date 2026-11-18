package com.crispprep.reviews.repository;

/** "user X has N of something" (grouped query result). */
public interface UserCount {
    Long getUserId();
    Long getN();
}
