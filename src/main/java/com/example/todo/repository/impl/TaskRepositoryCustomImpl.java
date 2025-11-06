package com.example.todo.repository.impl;

import com.example.todo.entity.Task;
import com.example.todo.entity.enums.TaskPriority;
import com.example.todo.entity.enums.TaskStatus;
import com.example.todo.repository.TaskRepositoryCustom;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class TaskRepositoryCustomImpl implements TaskRepositoryCustom {

    private final EntityManager em;

    public TaskRepositoryCustomImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public List<Task> findAllAccessible(UUID userId, String q, TaskStatus status, TaskPriority priority) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Task> cq = cb.createQuery(Task.class);
        Root<Task> task = cq.from(Task.class);
        Join<Object,Object> share = task.join("id", JoinType.LEFT);
        // The join above is placeholder to allow left join behavior; we'll use subquery for shares instead.

        List<Predicate> predicates = new ArrayList<>();
        Predicate ownerPredicate = cb.equal(task.get("owner").get("id"), userId);

        Subquery<Long> sq = cq.subquery(Long.class);
        Root<?> ts = sq.from(com.example.todo.entity.TaskShare.class);
        sq.select(cb.literal(1L));
        sq.where(cb.equal(ts.get("id").get("taskId"), task.get("id")),
                cb.equal(ts.get("id").get("userId"), userId));

        Predicate sharedPredicate = cb.exists(sq);
        predicates.add(cb.or(ownerPredicate, sharedPredicate));

        if (StringUtils.hasText(q)) {
            String like = "%" + q + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(task.get("title")), like.toLowerCase()),
                    cb.like(cb.lower(task.get("description")), like.toLowerCase()),
                    cb.like(cb.lower(task.get("category")), like.toLowerCase())
                    // tags text[] search is hard in Criteria; skip for dynamic query and rely on native as fallback
            ));
        }
        if (status != null) predicates.add(cb.equal(task.get("status"), status));
        if (priority != null) predicates.add(cb.equal(task.get("priority"), priority));

        cq.select(task).where(predicates.toArray(new Predicate[0]));
        TypedQuery<Task> query = em.createQuery(cq).setMaxResults(100);
        return query.getResultList();
    }
}
