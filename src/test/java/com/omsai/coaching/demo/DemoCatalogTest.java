package com.omsai.coaching.demo;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The curated catalogue is plain data, so it can be checked without a
 * database: every offering exists, every subject it needs exists, the
 * mappings of the streams are the right ones, nothing is duplicated
 * and no debug placeholder slipped in.
 */
class DemoCatalogTest {

    @Test
    void everyCourseIsDistinctAndProfessionallyNamed() {
        Set<String> names = new HashSet<>();
        for (DemoCatalog.CourseSeed course : DemoCatalog.COURSES) {
            assertThat(names.add(course.name()))
                    .as("duplicate course name: " + course.name())
                    .isTrue();
            assertThat(course.name()).doesNotContain("DBG");
            assertThat(course.name()).doesNotContain("Demo ");
            assertThat(course.name()).doesNotContain("Test ");
            assertThat(course.name()).doesNotContain("Sample ");
            assertThat(course.name()).doesNotContain("FF ");
            assertThat(course.name()).doesNotContain("FG ");
            assertThat(course.name()).isNotBlank();
            assertThat(course.category()).isNotBlank();
            assertThat(course.description()).isNotBlank();
            assertThat(course.durationMonths()).isBetween(1, 120);
        }
    }

    @Test
    void everySubjectIsDistinctAndProfessionallyNamed() {
        Set<String> names = new HashSet<>();
        for (DemoCatalog.SubjectSeed subject : DemoCatalog.SUBJECTS) {
            assertThat(names.add(subject.name()))
                    .as("duplicate subject name: " + subject.name())
                    .isTrue();
            assertThat(subject.name()).doesNotContain("Debug");
            assertThat(subject.name()).doesNotContain("Demo ");
            assertThat(subject.name()).doesNotContain("Test ");
            assertThat(subject.name()).doesNotContain("Sample ");
            assertThat(subject.description()).isNotBlank();
        }
    }

    @Test
    void everyCourseOnlyUsesSubjectsOfTheCatalogue() {
        Set<String> known = DemoCatalog.subjectsByName().keySet();
        for (DemoCatalog.CourseSeed course : DemoCatalog.COURSES) {
            assertThat(course.subjects())
                    .as("unknown subject in " + course.name())
                    .allMatch(known::contains);
            assertThat(course.subjects())
                    .as("a course must not repeat a subject: " + course.name())
                    .doesNotHaveDuplicates();
            assertThat(course.subjects())
                    .as("every course must teach something: " + course.name())
                    .isNotEmpty();
        }
    }

    @Test
    void theStandardElevenAndTwelveStreamsAreMappedCorrectly() {
        assertSubjectsOf("Standard 11 Science — PCM",
                "Physics", "Chemistry", "Mathematics", "English");
        assertSubjectsOf("Standard 11 Science — PCB",
                "Physics", "Chemistry", "Biology", "English");
        assertSubjectsOf("Standard 11 Science — PCMB",
                "Physics", "Chemistry", "Mathematics", "Biology", "English");
        assertSubjectsOf("Standard 12 Science — PCM",
                "Physics", "Chemistry", "Mathematics", "English");
        assertSubjectsOf("Standard 12 Science — PCB",
                "Physics", "Chemistry", "Biology", "English");
        assertSubjectsOf("Standard 12 Science — PCMB",
                "Physics", "Chemistry", "Mathematics", "Biology", "English");
    }

    @Test
    void theEntranceExamCoursesAreMappedCorrectly() {
        assertSubjectsOf("MHT-CET — PCM Target Batch",
                "Physics", "Chemistry", "Mathematics");
        assertSubjectsOf("MHT-CET — PCB Target Batch",
                "Physics", "Chemistry", "Biology");
        assertSubjectsOf("MHT-CET — PCM Crash Course",
                "Physics", "Chemistry", "Mathematics");
        assertSubjectsOf("MHT-CET — PCB Crash Course",
                "Physics", "Chemistry", "Biology");
        assertSubjectsOf("MHT-CET — Class 11 and 12 Combined Preparation",
                "Physics", "Chemistry", "Mathematics", "Biology");
        assertSubjectsOf("JEE Main — PCM Preparation",
                "Physics", "Chemistry", "Mathematics");
        assertSubjectsOf("JEE Main and Advanced — PCM Preparation",
                "Physics", "Chemistry", "Mathematics");
        assertSubjectsOf("NEET-UG — Medical Entrance Preparation",
                "Physics", "Chemistry", "Biology");
        assertSubjectsOf("NEET-UG — Intensive Revision",
                "Physics", "Chemistry", "Biology");
    }

    @Test
    void theEngineeringMathematicsOfferingsShareOneSubject() {
        assertSubjectsOf("Engineering Mathematics I", "Engineering Mathematics");
        assertSubjectsOf("Engineering Mathematics II", "Engineering Mathematics");
        assertSubjectsOf("Engineering Mathematics III", "Engineering Mathematics");
        assertSubjectsOf("Engineering Mathematics IV", "Engineering Mathematics");
        assertSubjectsOf("Applied Mathematics — University/Branch-Specific Coaching", "Applied Mathematics");
    }

    @Test
    void theSchoolCoursesDoNotAttachEverySubject() {
        DemoCatalog.CourseSeed standard7 = course("Standard 7 — Maharashtra State Board Tuition");
        DemoCatalog.CourseSeed standard9 = course("Standard 9 — Maharashtra SSC Tuition");
        DemoCatalog.CourseSeed foundation = course("Standard 8 — Mathematics and Science Foundation");

        // The full tuition teaches many subjects...
        assertThat(standard7.subjects()).hasSizeGreaterThan(5);
        assertThat(standard9.subjects()).hasSizeGreaterThan(5);
        // ...the foundation course teaches only what its name promises.
        assertThat(foundation.subjects())
                .containsExactlyInAnyOrder("Mathematics", "General Science");
    }

    @Test
    void theFoundationCoursesAreNotAdvertisedAsAnExaminationCategory() {
        DemoCatalog.CourseSeed jeeFoundation = course("JEE Foundation — School-Level Preparation");
        DemoCatalog.CourseSeed neetFoundation = course("NEET Foundation — School-Level Preparation");

        assertThat(jeeFoundation.category()).isEqualTo("Foundation Program");
        assertThat(neetFoundation.category()).isEqualTo("Foundation Program");
        assertThat(jeeFoundation.description()).contains("not an examination category");
        assertThat(neetFoundation.description()).contains("not an examination category");
    }

    @Test
    void theEngineeringCoursesDoNotClaimAUniversalSyllabus() {
        for (DemoCatalog.CourseSeed course : DemoCatalog.COURSES) {
            if ("Engineering Mathematics".equals(course.category())) {
                assertThat(course.description().toLowerCase(Locale.ROOT))
                        .as("an engineering course must mention that the syllabus varies: " + course.name())
                        .contains("university");
            }
        }
    }

    @Test
    void theTeachersAreDistinctAndTheirSubjectsExist() {
        Set<String> known = DemoCatalog.subjectsByName().keySet();
        Set<String> names = new HashSet<>();
        for (DemoCatalog.TeacherSeed teacher : DemoCatalog.TEACHERS) {
            assertThat(names.add(teacher.fullName()))
                    .as("duplicate teacher name: " + teacher.fullName())
                    .isTrue();
            assertThat(teacher.subjects()).isNotEmpty();
            assertThat(teacher.subjects()).allMatch(known::contains);
        }
    }

    @Test
    void everySubjectIsTaughtByAtLeastOneTeacher() {
        Set<String> taught = new HashSet<>();
        for (DemoCatalog.TeacherSeed teacher : DemoCatalog.TEACHERS) {
            taught.addAll(teacher.subjects());
        }
        assertThat(taught).containsExactlyInAnyOrderElementsOf(DemoCatalog.subjectsByName().keySet());
    }

    @Test
    void noTeacherTeachesEverySubject() {
        for (DemoCatalog.TeacherSeed teacher : DemoCatalog.TEACHERS) {
            assertThat(teacher.subjects().size())
                    .as("no teacher should teach the whole catalogue: " + teacher.fullName())
                    .isLessThan(DemoCatalog.SUBJECTS.size());
        }
    }

    @Test
    void theStudentsAreDistinctAndTheirCoursesExist() {
        Set<String> known = DemoCatalog.coursesByName().keySet();
        Set<String> names = new HashSet<>();
        for (DemoCatalog.StudentSeed student : DemoCatalog.STUDENTS) {
            assertThat(names.add(student.fullName()))
                    .as("duplicate student name: " + student.fullName())
                    .isTrue();
            assertThat(student.address()).isNotBlank();
            assertThat(student.courses()).isNotEmpty();
            assertThat(student.courses())
                    .as("unknown course for " + student.fullName())
                    .allMatch(known::contains);
            assertThat(student.courses()).doesNotHaveDuplicates();
        }
    }

    @Test
    void theExistingDemonstrationStudentsAreNotRepeated() {
        List<String> names = new ArrayList<>();
        DemoCatalog.STUDENTS.forEach(student -> names.add(student.fullName()));
        // The two demonstration students that already exist in the
        // development database must not be created a second time.
        assertThat(names).doesNotContain("Aarav Sharma");
        assertThat(names).doesNotContain("Dia Patel");
    }

    @Test
    void theSyntheticContactDetailsCannotReachAnybody() {
        // A mail address on the reserved domain can never resolve.
        String email = DemoDataSeeder.emailLocalPart("Sandeep Patil") + "@example.invalid";
        assertThat(email).isEqualTo("sandeep.patil@example.invalid");
        assertThat(email).endsWith("@example.invalid");

        // A phone number that cannot be an Indian mobile line: those
        // always start with 6 to 9.
        String phone = DemoDataSeeder.syntheticPhone(7);
        assertThat(phone).isEqualTo("0000000007");
        assertThat(phone).hasSize(10).startsWith("00");
        assertThat(phone.charAt(0)).isLessThan('6');
    }

    private DemoCatalog.CourseSeed course(String name) {
        return DemoCatalog.coursesByName().get(name);
    }

    private void assertSubjectsOf(String courseName, String... expected) {
        DemoCatalog.CourseSeed course = course(courseName);
        assertThat(course).as("missing course " + courseName).isNotNull();
        assertThat(course.subjects()).containsExactlyInAnyOrder(expected);
    }
}
