package com.elearning.learning.service;

import com.elearning.learning.constants.MockTests;
import com.elearning.learning.model.*;
import com.elearning.learning.entities.UserCourseDetails;
import com.elearning.learning.entities.UserTestResults;
import com.elearning.learning.repository.TestResultsRepository;
import com.elearning.learning.repository.UserCourseRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
@AllArgsConstructor
public class MockTestsService {

    private final TestResultsRepository testResultsRepository;
    private final UserCourseRepository userCourseRepository;

    public List<MockTestQuestions> getQuestions(String testType) {
        File file = new File(
                this.getClass().getClassLoader().getResource("src\\main\\resources\\mockTests"+testType+".json").getFile()
        );
        ObjectMapper mapper = new ObjectMapper();
        List<MockTestQuestions> mockTestQuestions = null;
        try {
            mockTestQuestions = mapper.readValue(file, new TypeReference<List<MockTestQuestions>>() {
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mockTestQuestions;
    }

    public UserTestDetails getAuthenticatedMockTests(String username) throws JsonProcessingException {
        UserCourseDetails userCourseDetails = userCourseRepository.findByUsername(username);
        List<String> allowedTests = new ArrayList<>();
        List<String> submittedTests = new ArrayList<>();
        if(userCourseDetails.getAllowedMockTests() != null && !StringUtils.isEmpty(userCourseDetails.getAllowedMockTests())) {
            ObjectMapper mapper = new ObjectMapper();
            List<CourseMapper> testList = mapper.readValue(userCourseDetails.getAllowedMockTests(), new TypeReference<List<CourseMapper>>(){});
            for(CourseMapper test : testList) {
                if(new Date(test.getEndTime()).after(new Date())) {
                    allowedTests.add(test.getId());
                }
            }
        }
        if(userCourseDetails.getSubmittedMockTests() != null && !userCourseDetails.getSubmittedMockTests().isEmpty()) {
            if(userCourseDetails.getSubmittedMockTests().indexOf(",") != -1){
                submittedTests = Arrays.asList(userCourseDetails.getSubmittedMockTests().split(","));
            } else {
                submittedTests.add(userCourseDetails.getSubmittedMockTests());
            }
        }
        List<String> submittedTestCopy = submittedTests;
        for(String submittedTest: submittedTestCopy) {
            if(!allowedTests.contains(submittedTest)) {
                submittedTests.remove(submittedTest);
            }
        }
        UserTestDetails userTestDetails = new UserTestDetails(allowedTests, submittedTests);
        return userTestDetails;
    }
    @Transactional
    public TestResultResponse evaluateResults(TestResultRequest testResultRequest) {
        String username = testResultRequest.getUsername();
        String testName = testResultRequest.getTestName();

        List<Integer> storedAnswers = MockTests.getAnswers(testName);
        int i =0;
        int correctAnswers = 0;
        int wrongAnswers = 0;
        int unAnsweredQuestions = 0;
        int totalQuestions = 75;
        for(int givenAnswer : testResultRequest.getAnswer()) {
            if(givenAnswer == -1){
                unAnsweredQuestions = unAnsweredQuestions + 1;
            }
            else if(givenAnswer == storedAnswers.get(i)) {
                correctAnswers = correctAnswers + 1;
            }
            else if(givenAnswer != storedAnswers.get(i)) {
                wrongAnswers = wrongAnswers + 1;
            }
        }
        TestResultResponse testResultResponse = new TestResultResponse(correctAnswers, totalQuestions, totalQuestions - unAnsweredQuestions,  testResultRequest.getTimeTaken(),
                testResultRequest.getStartTime(), testResultRequest.getEndTime());

        UserTestResults userTestResults = new UserTestResults();
        userTestResults.setUsername(username);
        userTestResults.setTestType(testName);
        userTestResults.setCorrectAnswers(correctAnswers);
        userTestResults.setAttemptedQuestions(totalQuestions - unAnsweredQuestions);
        userTestResults.setTimeTaken(testResultRequest.getTimeTaken());
        userTestResults.setStartTime(testResultRequest.getStartTime());
        userTestResults.setEndTime(testResultRequest.getEndTime());
        testResultsRepository.save(userTestResults);

        UserCourseDetails userCourseDetails = userCourseRepository.findByUsername(username);
        String submittedTest = testName;
        if(!StringUtils.isEmpty(userCourseDetails.getSubmittedMockTests())) {
            submittedTest = userCourseDetails.getSubmittedMockTests().concat(",").concat(testName);
        }
        userCourseRepository.updateSubmittedTest(username, submittedTest);

        return testResultResponse;
    }

    public TestResultResponse getResults(String username, String testType) {
        UserTestResults userTestResults = testResultsRepository.findByTestTypeAndUsername(testType, username);
        TestResultResponse testResultResponse = new TestResultResponse(userTestResults.getCorrectAnswers(), 75, userTestResults.getAttemptedQuestions(), userTestResults.getTimeTaken(),
                userTestResults.getStartTime(), userTestResults.getEndTime());

        return testResultResponse;
    }
}
