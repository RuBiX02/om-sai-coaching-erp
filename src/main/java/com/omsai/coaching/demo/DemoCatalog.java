package com.omsai.coaching.demo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The curated catalogue of the Om Sai Coaching demo dataset.
 *
 * <p>Everything in here is <strong>fictional demonstration content</strong>.
 * The people are invented; no real student, teacher or parent is
 * represented. The course names follow the offerings a Maharashtra
 * coaching institute actually teaches; the subject mappings are
 * deliberately conservative and claim no examination guarantee.</p>
 *
 * <p>The catalogue is plain data: it is the single place where the
 * demo dataset is defined, and the seeder only ever reads from it.</p>
 */
public final class DemoCatalog {

    private DemoCatalog() {
    }

    /** The academic year the demo batches run in. */
    public static final String ACADEMIC_YEAR = "2026-27";

    /** A subject of the catalogue with a short, factual description. */
    public record SubjectSeed(String name, String description) {
    }

    /** A course of the catalogue with the subjects it teaches. */
    public record CourseSeed(String name,
                             String category,
                             String description,
                             Integer durationMonths,
                             List<String> subjects) {
    }

    /** A demo teacher and the subjects of the catalogue they teach. */
    public record TeacherSeed(String fullName, List<String> subjects) {
    }

    /** A demo student, the town they come from and the courses they attend. */
    public record StudentSeed(String fullName, String address, List<String> courses) {
    }

    // ----- subjects ---------------------------------------------------------

    public static final List<SubjectSeed> SUBJECTS = List.of(
            new SubjectSeed("Mathematics",
                    "Arithmetic, algebra, geometry, mensuration and data handling across the school "
                            + "and senior secondary programmes."),
            new SubjectSeed("Mathematics Part I — Algebra",
                    "Algebraic expressions, equations, sequences and the graph of a linear equation."),
            new SubjectSeed("Mathematics Part II — Geometry",
                    "Lines and angles, triangles, circles, similarity and coordinate geometry."),
            new SubjectSeed("General Science",
                    "Physics, chemistry and biology basics for the middle school, with practical work."),
            new SubjectSeed("Science and Technology",
                    "The Maharashtra Board Class 9 and 10 science and technology syllabus."),
            new SubjectSeed("Physics",
                    "Mechanics, thermal physics, waves, optics, electricity and modern physics."),
            new SubjectSeed("Chemistry",
                    "Physical, inorganic and organic chemistry with numerical practice."),
            new SubjectSeed("Biology",
                    "Botany and zoology: cell structure, human physiology, genetics and ecology."),
            new SubjectSeed("English",
                    "Grammar, comprehension, vocabulary and writing practice."),
            new SubjectSeed("Marathi",
                    "Grammar, prose, poetry and writing skills."),
            new SubjectSeed("Hindi",
                    "Grammar, prose, poetry and writing skills."),
            new SubjectSeed("History and Civics",
                    "History, civics and the political framework of the middle school syllabus."),
            new SubjectSeed("History and Political Science",
                    "The Maharashtra Board Class 9 and 10 history and political science syllabus."),
            new SubjectSeed("Geography",
                    "Physical geography, practical geography and map work."),
            new SubjectSeed("Computer Studies",
                    "Computer fundamentals, productivity tools and basic programming."),
            new SubjectSeed("Information Technology",
                    "The Maharashtra Board Information Technology syllabus."),
            new SubjectSeed("Engineering Mathematics",
                    "The engineering mathematics syllabus of the affiliated university; the scheme and "
                            + "the chapter order differ between universities and branches."),
            new SubjectSeed("Applied Mathematics",
                    "University and branch specific mathematics coaching; the content follows the "
                            + "syllabus of the affiliated university."));

    // ----- courses ----------------------------------------------------------

    private static final List<String> SCHOOL_7_8 = List.of(
            "Mathematics", "General Science", "English", "Marathi", "Hindi",
            "History and Civics", "Geography", "Computer Studies");

    private static final List<String> SCHOOL_9_10 = List.of(
            "Mathematics Part I — Algebra", "Mathematics Part II — Geometry", "Science and Technology",
            "English", "Marathi", "Hindi", "History and Political Science", "Geography",
            "Information Technology");

    private static final List<String> PCM = List.of(
            "Physics", "Chemistry", "Mathematics", "English");

    private static final List<String> PCB = List.of(
            "Physics", "Chemistry", "Biology", "English");

    private static final List<String> PCMB = List.of(
            "Physics", "Chemistry", "Mathematics", "Biology", "English");

    public static final List<CourseSeed> COURSES = List.of(
            // ----- school tuition: standards 7 and 8 -----
            new CourseSeed("Standard 7 — Maharashtra State Board Tuition",
                    "School Tuition",
                    "Full school tuition for Standard 7 following the Maharashtra State Board syllabus.",
                    12, SCHOOL_7_8),
            new CourseSeed("Standard 8 — Maharashtra State Board Tuition",
                    "School Tuition",
                    "Full school tuition for Standard 8 following the Maharashtra State Board syllabus.",
                    12, SCHOOL_7_8),
            new CourseSeed("Standard 7 — Mathematics Foundation",
                    "School Tuition",
                    "Mathematics and general science foundation for Standard 7, for students who need "
                            + "the core concepts strengthened before the board year.",
                    6, List.of("Mathematics", "General Science")),
            new CourseSeed("Standard 8 — Mathematics and Science Foundation",
                    "School Tuition",
                    "Mathematics and science foundation for Standard 8, for students who need the core "
                            + "concepts strengthened before the board year.",
                    6, List.of("Mathematics", "General Science")),

            // ----- school tuition: standards 9 and 10 -----
            new CourseSeed("Standard 9 — Maharashtra SSC Tuition",
                    "School Tuition",
                    "Full school tuition for Standard 9 following the Maharashtra SSC Board syllabus.",
                    12, SCHOOL_9_10),
            new CourseSeed("Standard 10 — Maharashtra SSC Board Preparation",
                    "School Tuition",
                    "Full school tuition for Standard 10 with board examination preparation across "
                            + "every subject of the Maharashtra SSC Board syllabus.",
                    12, SCHOOL_9_10),
            new CourseSeed("Standard 9 — Mathematics and Science",
                    "School Tuition",
                    "Mathematics and science tuition for Standard 9, for students who take these two "
                            + "subjects as their focus.",
                    12, List.of("Mathematics Part I — Algebra", "Mathematics Part II — Geometry",
                            "Science and Technology")),
            new CourseSeed("Standard 10 — Mathematics and Science Board Preparation",
                    "School Tuition",
                    "Mathematics and science board preparation for Standard 10, for students who take "
                            + "these two subjects as their focus.",
                    12, List.of("Mathematics Part I — Algebra", "Mathematics Part II — Geometry",
                            "Science and Technology")),

            // ----- senior secondary: standard 11 -----
            new CourseSeed("Standard 11 Science — PCM",
                    "Senior Secondary",
                    "Standard 11 science with the physics, chemistry and mathematics stream.",
                    12, PCM),
            new CourseSeed("Standard 11 Science — PCB",
                    "Senior Secondary",
                    "Standard 11 science with the physics, chemistry and biology stream.",
                    12, PCB),
            new CourseSeed("Standard 11 Science — PCMB",
                    "Senior Secondary",
                    "Standard 11 science with physics, chemistry, mathematics and biology together.",
                    12, PCMB),

            // ----- senior secondary: standard 12 -----
            new CourseSeed("Standard 12 Science — PCM",
                    "Senior Secondary",
                    "Standard 12 science with the physics, chemistry and mathematics stream, including "
                            + "board examination preparation.",
                    12, PCM),
            new CourseSeed("Standard 12 Science — PCB",
                    "Senior Secondary",
                    "Standard 12 science with the physics, chemistry and biology stream, including "
                            + "board examination preparation.",
                    12, PCB),
            new CourseSeed("Standard 12 Science — PCMB",
                    "Senior Secondary",
                    "Standard 12 science with physics, chemistry, mathematics and biology together, "
                            + "including board examination preparation.",
                    12, PCMB),
            new CourseSeed("Standard 12 Science — Board Examination Preparation",
                    "Senior Secondary",
                    "Revision and examination preparation across the whole Standard 12 science "
                            + "syllabus, for students who sit the board examination this year.",
                    8, PCMB),

            // ----- MHT-CET -----
            new CourseSeed("MHT-CET — PCM Target Batch",
                    "MHT-CET Preparation",
                    "Two-year preparation for the Maharashtra Common Entrance Test in the physics, "
                            + "chemistry and mathematics combination.",
                    24, List.of("Physics", "Chemistry", "Mathematics")),
            new CourseSeed("MHT-CET — PCB Target Batch",
                    "MHT-CET Preparation",
                    "Two-year preparation for the Maharashtra Common Entrance Test in the physics, "
                            + "chemistry and biology combination.",
                    24, List.of("Physics", "Chemistry", "Biology")),
            new CourseSeed("MHT-CET — PCM Crash Course",
                    "MHT-CET Preparation",
                    "Intensive revision course for the Maharashtra Common Entrance Test in the physics, "
                            + "chemistry and mathematics combination.",
                    4, List.of("Physics", "Chemistry", "Mathematics")),
            new CourseSeed("MHT-CET — PCB Crash Course",
                    "MHT-CET Preparation",
                    "Intensive revision course for the Maharashtra Common Entrance Test in the physics, "
                            + "chemistry and biology combination.",
                    4, List.of("Physics", "Chemistry", "Biology")),
            new CourseSeed("MHT-CET — Class 11 and 12 Combined Preparation",
                    "MHT-CET Preparation",
                    "Preparation for the Maharashtra Common Entrance Test that follows the Class 11 and "
                            + "Class 12 science syllabus, for students who need both years covered.",
                    24, List.of("Physics", "Chemistry", "Mathematics", "Biology")),

            // ----- JEE -----
            new CourseSeed("JEE Main — PCM Preparation",
                    "JEE Preparation",
                    "Preparation for the JEE Main examination in the physics, chemistry and mathematics "
                            + "combination.",
                    24, List.of("Physics", "Chemistry", "Mathematics")),
            new CourseSeed("JEE Main and Advanced — PCM Preparation",
                    "JEE Preparation",
                    "Preparation for the JEE Main and Advanced examinations in the physics, chemistry "
                            + "and mathematics combination.",
                    24, List.of("Physics", "Chemistry", "Mathematics")),
            new CourseSeed("JEE Foundation — School-Level Preparation",
                    "Foundation Program",
                    "The institute's own foundation programme for school-level students who want to "
                            + "prepare for engineering entrance examinations early. This is a coaching "
                            + "offering of the institute, not an examination category.",
                    12, List.of("Physics", "Chemistry", "Mathematics")),

            // ----- NEET -----
            new CourseSeed("NEET-UG — Medical Entrance Preparation",
                    "NEET Preparation",
                    "Preparation for the NEET-UG medical entrance examination in physics, chemistry "
                            + "and biology.",
                    24, List.of("Physics", "Chemistry", "Biology")),
            new CourseSeed("NEET-UG — Intensive Revision",
                    "NEET Preparation",
                    "Intensive revision course for the NEET-UG medical entrance examination.",
                    4, List.of("Physics", "Chemistry", "Biology")),
            new CourseSeed("NEET Foundation — School-Level Preparation",
                    "Foundation Program",
                    "The institute's own foundation programme for school-level students who want to "
                            + "prepare for medical entrance examinations early. This is a coaching "
                            + "offering of the institute, not an examination category.",
                    12, List.of("Physics", "Chemistry", "Biology")),

            // ----- engineering mathematics -----
            new CourseSeed("Engineering Mathematics I",
                    "Engineering Mathematics",
                    "Semester one engineering mathematics coaching. The syllabus follows the scheme "
                            + "of the affiliated university, which differs between universities and "
                            + "branches.",
                    6, List.of("Engineering Mathematics")),
            new CourseSeed("Engineering Mathematics II",
                    "Engineering Mathematics",
                    "Semester two engineering mathematics coaching. The syllabus follows the scheme "
                            + "of the affiliated university, which differs between universities and "
                            + "branches.",
                    6, List.of("Engineering Mathematics")),
            new CourseSeed("Engineering Mathematics III",
                    "Engineering Mathematics",
                    "Semester three engineering mathematics coaching. The syllabus follows the scheme "
                            + "of the affiliated university, which differs between universities and "
                            + "branches.",
                    6, List.of("Engineering Mathematics")),
            new CourseSeed("Engineering Mathematics IV",
                    "Engineering Mathematics",
                    "Semester four engineering mathematics coaching. The syllabus follows the scheme "
                            + "of the affiliated university, which differs between universities and "
                            + "branches.",
                    6, List.of("Engineering Mathematics")),
            new CourseSeed("Applied Mathematics — University/Branch-Specific Coaching",
                    "Engineering Mathematics",
                    "Applied mathematics coaching built around the syllabus of a specific university "
                            + "and branch. The chapters are agreed with the student and are not part "
                            + "of the standard course list.",
                    6, List.of("Applied Mathematics")));

    // ----- teachers ---------------------------------------------------------

    /**
     * The demo teachers and the catalogue subjects they teach. A teacher
     * may teach more than one subject; no teacher teaches everything.
     */
    public static final List<TeacherSeed> TEACHERS = List.of(
            new TeacherSeed("Sandeep Patil",
                    List.of("Mathematics", "Mathematics Part I — Algebra", "Mathematics Part II — Geometry")),
            new TeacherSeed("Neha Kulkarni",
                    List.of("Physics", "Science and Technology")),
            new TeacherSeed("Amit Jadhav",
                    List.of("Chemistry", "General Science")),
            new TeacherSeed("Pooja Deshmukh",
                    List.of("Biology", "General Science")),
            new TeacherSeed("Rahul Shinde",
                    List.of("Physics", "Engineering Mathematics")),
            new TeacherSeed("Kavita Pawar",
                    List.of("History and Civics", "History and Political Science", "Geography")),
            new TeacherSeed("Sneha More",
                    List.of("English", "Marathi", "Hindi")),
            new TeacherSeed("Imran Shaikh",
                    List.of("Mathematics", "Mathematics Part I — Algebra", "Computer Studies",
                            "Information Technology")),
            new TeacherSeed("Rohan Joshi",
                    List.of("Engineering Mathematics", "Applied Mathematics",
                            "Mathematics Part II — Geometry")));

    // ----- students ---------------------------------------------------------

    /**
     * The demo students, the town they come from and the courses they
     * attend. The two demonstration students that already existed in the
     * development database are deliberately not repeated here.
     */
    public static final List<StudentSeed> STUDENTS = List.of(
            new StudentSeed("Ananya Patil", "Rahuri",
                    List.of("Standard 8 — Maharashtra State Board Tuition",
                            "MHT-CET — Class 11 and 12 Combined Preparation")),
            new StudentSeed("Vedant Jadhav", "Ahilyanagar",
                    List.of("Standard 10 — Maharashtra SSC Board Preparation",
                            "JEE Main — PCM Preparation")),
            new StudentSeed("Sai Shinde", "Sangamner",
                    List.of("Standard 9 — Maharashtra SSC Tuition",
                            "MHT-CET — PCM Target Batch")),
            new StudentSeed("Isha Kulkarni", "Shirdi",
                    List.of("Standard 7 — Maharashtra State Board Tuition",
                            "Standard 7 — Mathematics Foundation")),
            new StudentSeed("Omkar More", "Nashik",
                    List.of("Standard 12 Science — PCMB",
                            "NEET-UG — Medical Entrance Preparation")),
            new StudentSeed("Tanvi Gaikwad", "Pune",
                    List.of("Standard 11 Science — PCM",
                            "MHT-CET — PCM Crash Course")),
            new StudentSeed("Atharva Chavan", "Satara",
                    List.of("Standard 11 Science — PCB",
                            "NEET-UG — Intensive Revision")),
            new StudentSeed("Riya Bhosale", "Kolhapur",
                    List.of("Standard 11 Science — PCMB",
                            "MHT-CET — Class 11 and 12 Combined Preparation")),
            new StudentSeed("Pranav Pawar", "Nagpur",
                    List.of("Standard 12 Science — PCB",
                            "NEET-UG — Medical Entrance Preparation")),
            new StudentSeed("Aditi Joshi", "Chhatrapati Sambhajinagar",
                    List.of("Standard 12 Science — Board Examination Preparation",
                            "MHT-CET — PCB Target Batch")),
            new StudentSeed("Yash Wagh", "Rahuri",
                    List.of("Standard 9 — Mathematics and Science",
                            "JEE Foundation — School-Level Preparation")),
            new StudentSeed("Snehal Kadam", "Nashik",
                    List.of("Standard 10 — Mathematics and Science Board Preparation",
                            "MHT-CET — PCB Crash Course")),
            new StudentSeed("Zoya Shaikh", "Sangamner",
                    List.of("Standard 11 Science — PCM",
                            "JEE Main and Advanced — PCM Preparation")),
            new StudentSeed("Arjun Reddy", "Pune",
                    List.of("Standard 12 Science — PCM",
                            "JEE Main — PCM Preparation")),
            new StudentSeed("Meera Nair", "Kolhapur",
                    List.of("Standard 11 Science — PCB",
                            "NEET Foundation — School-Level Preparation")),
            new StudentSeed("Kabir Khan", "Nagpur",
                    List.of("Standard 12 Science — PCMB",
                            "JEE Main and Advanced — PCM Preparation")),
            new StudentSeed("Rohan Desai", "Ahilyanagar",
                    List.of("Engineering Mathematics I", "Engineering Mathematics II")),
            new StudentSeed("Priya Yadav", "Shirdi",
                    List.of("Engineering Mathematics III",
                            "Applied Mathematics — University/Branch-Specific Coaching")));

    // ----- batch naming -----------------------------------------------------

    /** The demo batch name of a course, chosen by its category. */
    public static String batchNameFor(CourseSeed course) {
        return switch (course.category()) {
            case "School Tuition" -> "Morning Batch";
            case "Senior Secondary" -> "Evening Batch";
            case "Foundation Program" -> "Foundation Batch";
            case "Engineering Mathematics" -> "Semester Batch";
            default -> "Target Batch";
        };
    }

    /** The catalogue subjects indexed by their name. */
    public static Map<String, SubjectSeed> subjectsByName() {
        Map<String, SubjectSeed> byName = new LinkedHashMap<>();
        for (SubjectSeed subject : SUBJECTS) {
            byName.put(subject.name(), subject);
        }
        return byName;
    }

    /** The catalogue courses indexed by their name. */
    public static Map<String, CourseSeed> coursesByName() {
        Map<String, CourseSeed> byName = new LinkedHashMap<>();
        for (CourseSeed course : COURSES) {
            byName.put(course.name(), course);
        }
        return byName;
    }
}
