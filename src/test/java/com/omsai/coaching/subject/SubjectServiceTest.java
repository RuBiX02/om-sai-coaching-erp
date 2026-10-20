package com.omsai.coaching.subject;

import com.omsai.coaching.common.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Subject management rules: creation, unique names and renaming.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubjectServiceTest {

    @Autowired
    private SubjectService subjectService;

    private SubjectForm form(String name) {
        SubjectForm form = new SubjectForm();
        form.setName(name);
        return form;
    }

    @Test
    void create_persistsSubject() {
        Subject subject = subjectService.create(form("World History"));
        assertThat(subject.getId()).isNotNull();
        assertThat(subjectService.get(subject.getId()).getName()).isEqualTo("World History");
    }

    @Test
    void create_rejectsDuplicateName() {
        subjectService.create(form("Civic Studies"));
        assertThatThrownBy(() -> subjectService.create(form("civic studies")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void update_renamesSubject() {
        Subject subject = subjectService.create(form("Old Name"));
        Subject updated = subjectService.update(subject.getId(), form("New Name"));
        assertThat(updated.getName()).isEqualTo("New Name");
    }

    @Test
    void update_rejectsNameUsedByAnotherSubject() {
        Subject other = subjectService.create(form("First Occupied"));
        subjectService.create(form("Second Subject"));

        assertThatThrownBy(() -> subjectService.update(other.getId(), form("second subject")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void search_findsSubjectsByName() {
        subjectService.create(form("Fictional Search Subject"));
        var page = subjectService.search("fictional search", PageRequest.of(0, 10));
        assertThat(page.getContent()).isNotEmpty();
    }
}
