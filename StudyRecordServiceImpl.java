package cn.com.bluemoon.admin.web.personalFile.service.impl;

import cn.com.bluemoon.admin.web.api.req.CourseSuiteListReq;
import cn.com.bluemoon.admin.web.api.req.CourseTypeListReq;
import cn.com.bluemoon.admin.web.api.resp.CourseListResp;
import cn.com.bluemoon.admin.web.api.resp.CourseTypeResp;
import cn.com.bluemoon.admin.web.api.service.IHDTrainingDubboService;
import cn.com.bluemoon.admin.web.common.filter.UserContext;
import cn.com.bluemoon.admin.web.common.response.ResultPage;
import cn.com.bluemoon.admin.web.personalFile.mapper.StudyRecordSqlMapper;
import cn.com.bluemoon.admin.web.personalFile.service.StudyRecordService;
import cn.com.bluemoon.admin.web.personalFile.vo.studyRecord.*;
import cn.com.bluemoon.training.dubbo.api.TrainStudentInfoService;
import cn.com.bluemoon.training.dubbo.vo.*;
import cn.com.bluemoon.utils.DateUtil;
import com.alibaba.dubbo.config.annotation.Reference;
import com.baomidou.mybatisplus.extension.api.R;
import com.google.common.base.Joiner;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.formula.functions.T;
import org.checkerframework.checker.units.qual.C;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @ClassName StudyRecordServiceImpl
 * @Description
 * @Author CXB
 * @Date 2023/5/31 13:24
 */
@Service
@Slf4j
public class StudyRecordServiceImpl implements StudyRecordService {

    @Autowired
    private StudyRecordSqlMapper studyRecordSqlMapper;

    @Reference(check = false)
    private TrainStudentInfoService trainStudentInfoService;

    @Reference(check = false, version = "1.0.0", timeout = 100000)
    private IHDTrainingDubboService ihdTrainingDubboService;

    @Override
    public ResultPage<TrainingVo> pageTraining(TrainingQueryVo queryVo) throws Exception {
        String empCode = String.valueOf(81001605);
        Map<String, List<TrainingExperienceCategoryVO>> categoryMap = new HashMap<>();
        boolean categoryFlag = !CollectionUtils.isEmpty(queryVo.getCategorys());
        if (categoryFlag){
            categoryMap = queryVo.getCategorys().stream().collect(Collectors.groupingBy(TrainingExperienceCategoryVO::getSourceType));
        }
        // 获取全景档案培训经历列表
        List<TrainingVo> panoramaTrainingClassificationList = listPanoramaTrainingClassification(empCode, categoryMap.get("全景档案培训经历"), categoryFlag, queryVo);
        // 获取过往培训经历列表
        List<TrainingVo> pastTrainingList = listPastTraining(empCode, categoryMap.get("往培训经历"), categoryFlag, queryVo);
        // 获取线培训系统线上培训经历
        List<TrainingVo> courseInfoList = getCourseInfoList(empCode, queryVo, categoryMap.get("培训系统-线上培训"), categoryFlag);
        // 获取线培训系统线下培训经历
        List<TrainingVo> offlineTrainingList = listTraining(empCode, queryVo,  categoryMap.get("培训系统-线下培训"), categoryFlag);
        List<TrainingVo> totalList = Stream.of(panoramaTrainingClassificationList, pastTrainingList, courseInfoList, offlineTrainingList).flatMap(Collection::stream).collect(Collectors.toList());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        List<TrainingVo> results = null;
        if (!CollectionUtils.isEmpty(totalList)){
            results = totalList.stream()
                    .sorted(Comparator.comparing((TrainingVo e) -> {
                        if (e.getTrainingDate().length() > 10) {
                            String endDateString = e.getTrainingDate().substring(10);
                            return LocalDate.parse(endDateString, formatter);
                        }
                        return LocalDate.parse(e.getTrainingDate(), formatter);
                    }).reversed()).collect(Collectors.toList());
        }
        return ResultPage.getPageData(results, queryVo.getPageNum(), queryVo.getPageSize());
    }

    @Override
    public List<TrainingExperienceCategoryGroupVO> listTrainingClassify() throws Exception {
        String empCode = String.valueOf(81001605);
        List<TrainingExperienceCategoryGroupVO> results = new ArrayList<>();
        // 获取全景档案-培训经历-分类列表
        List<TrainingExperienceCategoryGroupVO> panoramaTrainingClassifications = getPanoramaTrainingClassification(empCode);
        if (!CollectionUtils.isEmpty(panoramaTrainingClassifications)) results.addAll(panoramaTrainingClassifications);
        // 获取过往经历-分类列表
        List<TrainingExperienceCategoryGroupVO> trainingExperienceCategories = getTrainingExperienceCategories();
        if (!CollectionUtils.isEmpty(trainingExperienceCategories)) results.addAll(trainingExperienceCategories);
        // 培训线上分类列表
        List<TrainingExperienceCategoryGroupVO> trainingTypeList = getTrainingTypeList(empCode);
        if (!CollectionUtils.isEmpty(trainingTypeList)) results.addAll(trainingTypeList);
        // 培训线下分类列表
        List<TrainingExperienceCategoryGroupVO> studentFileClassification = getStudentFileClassification(empCode, 1);
        if (!CollectionUtils.isEmpty(studentFileClassification)) results.addAll(studentFileClassification);

        // 一级分类相同的分组
        if (!CollectionUtils.isEmpty(results)) {
            Map<String, List<TrainingExperienceCategoryGroupVO>> trainingExperienceCategoryGroupVOMap = results.stream().collect(Collectors.groupingBy(TrainingExperienceCategoryGroupVO::getCategoryCode));
            List<TrainingExperienceCategoryGroupVO> finalResults = new ArrayList<>();
            trainingExperienceCategoryGroupVOMap.entrySet().forEach(e -> {
                TrainingExperienceCategoryGroupVO vo = new TrainingExperienceCategoryGroupVO();
                List<TrainingExperienceCategoryGroupVO> trainingExperienceCategoryList = e.getValue();
                vo.setCategoryCode(trainingExperienceCategoryList.get(0).getCategoryCode());
                vo.setCategoryName(trainingExperienceCategoryList.get(0).getCategoryName());
                vo.setSourceType(trainingExperienceCategoryList.get(0).getSourceType());
                List<TrainingExperienceCategoryGroupVO> childs = new ArrayList<>();
                for (TrainingExperienceCategoryGroupVO trainingExperienceCategoryGroup : trainingExperienceCategoryList){
                    if (!CollectionUtils.isEmpty(trainingExperienceCategoryGroup.getChilds())) {
                        childs.addAll(trainingExperienceCategoryGroup.getChilds());
                    }
                }
                vo.setChilds(childs);
                finalResults.add(vo);
            });
            results = finalResults;
        }
        return results;
    }

    @Override
    public List<TrainingExperienceCategoryGroupVO> listGiveLessonsClassify() throws Exception {
        String empCode = String.valueOf(33357);
        List<TrainingExperienceCategoryGroupVO> results = new ArrayList<>();
        // 获取全景档案-培训经历-分类列表
        List<TrainingExperienceCategoryGroupVO> panoramaTrainingClassifications = getPanoramaTrainingGiveLessonsClassify(empCode);
        if (!CollectionUtils.isEmpty(panoramaTrainingClassifications)) results.addAll(panoramaTrainingClassifications);
        // 授课线下分类列表
        List<TrainingExperienceCategoryGroupVO> studentFileClassification = getStudentFileClassification(empCode, 2);
        if (!CollectionUtils.isEmpty(studentFileClassification)) results.addAll(studentFileClassification);

        // 一级分类相同的分组
        if (!CollectionUtils.isEmpty(results)) {
            Map<String, List<TrainingExperienceCategoryGroupVO>> trainingExperienceCategoryGroupVOMap = results.stream().collect(Collectors.groupingBy(TrainingExperienceCategoryGroupVO::getCategoryCode));
            List<TrainingExperienceCategoryGroupVO> finalResults = new ArrayList<>();
            trainingExperienceCategoryGroupVOMap.entrySet().forEach(e -> {
                TrainingExperienceCategoryGroupVO vo = new TrainingExperienceCategoryGroupVO();
                List<TrainingExperienceCategoryGroupVO> trainingExperienceCategoryList = e.getValue();
                vo.setCategoryCode(trainingExperienceCategoryList.get(0).getCategoryCode());
                vo.setCategoryName(trainingExperienceCategoryList.get(0).getCategoryName());
                vo.setSourceType(trainingExperienceCategoryList.get(0).getSourceType());
                List<TrainingExperienceCategoryGroupVO> childs = new ArrayList<>();
                for (TrainingExperienceCategoryGroupVO trainingExperienceCategoryGroup : trainingExperienceCategoryList){
                    if (!CollectionUtils.isEmpty(trainingExperienceCategoryGroup.getChilds())) {
                        childs.addAll(trainingExperienceCategoryGroup.getChilds());
                    }
                }
                vo.setChilds(childs);
                finalResults.add(vo);
            });
            results = finalResults;
        }
        return results;
    }

    @Override
    public ResultPage<TeachingExperienceVo> pageGiveLessons(TrainingQueryVo queryVo) {
        String empCode = String.valueOf(33357);
        Map<String, List<TrainingExperienceCategoryVO>> categoryMap = new HashMap<>();
        boolean categoryFlag = !CollectionUtils.isEmpty(queryVo.getCategorys());
        if (categoryFlag){
            categoryMap = queryVo.getCategorys().stream().collect(Collectors.groupingBy(TrainingExperienceCategoryVO::getSourceType));
        }
        long startDate = System.currentTimeMillis();
        // 获取全景档案培训经历列表
        List<TeachingExperienceVo> panoramaGiveLessons = listPanoramaGiveLessons(empCode, categoryMap.get("全景档案授课经历"), categoryFlag, queryVo);
        log.info("获取全景档案培训经历列表耗时:{}", (System.currentTimeMillis() - startDate));
        startDate = System.currentTimeMillis();
        // 获取线培训系统线下培训经历
        List<TeachingExperienceVo> offlineTrainingList = getStudentFileTeachingExperience(empCode, queryVo,  categoryMap.get("培训系统-线下授课"), categoryFlag);
        log.info("获取线培训系统线下培训经历:{}", (System.currentTimeMillis() - startDate));
        List<TeachingExperienceVo> totalList = Stream.of(panoramaGiveLessons, offlineTrainingList).flatMap(Collection::stream).collect(Collectors.toList());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        List<TeachingExperienceVo> results = null;
        if (!CollectionUtils.isEmpty(totalList)){
            results = totalList.stream()
                    .sorted(Comparator.comparing((TeachingExperienceVo e) -> {
                        if (e.getTrainingDate().length() > 10) {
                            String endDateString = e.getTrainingDate().substring(10);
                            return LocalDate.parse(endDateString, formatter);
                        }
                        return LocalDate.parse(e.getTrainingDate(), formatter);
                    }).reversed()).collect(Collectors.toList());
        }
        return ResultPage.getPageData(results, queryVo.getPageNum(), queryVo.getPageSize());
    }

    private List<TeachingExperienceVo> listPanoramaGiveLessons(String empCode, List<TrainingExperienceCategoryVO> list, boolean categoryFlag, TrainingQueryVo queryVo) {
        if (!categoryFlag) {
            list = studyRecordSqlMapper.getPanoramaTrainingGiveLessonsClassify(empCode);
        }
        if (!CollectionUtils.isEmpty(list)) {
            List<TeachingExperienceVo> results = Collections.synchronizedList(new ArrayList<>());
            list.parallelStream().forEach(e -> {
                String categoryCode = !"4/999".equals(e.getSecondCategoryCode()) ? e.getSecondCategoryCode() : e.getFirstCategoryCode();
                List<TeachingExperienceVo> trainingVoList = studyRecordSqlMapper.listPanoramaGiveLessons(empCode, categoryCode, queryVo);
                if (!CollectionUtils.isEmpty(trainingVoList)){
                    String categoryName = e.getFirstCategoryName() + "/" + e.getSecondCategoryName();
                    trainingVoList.stream().forEach(trainingVo -> {
                        trainingVo.setCategory(categoryName);
                        trainingVo.setSource("全景档案授课经历");
                    });
                }
                results.addAll(trainingVoList);
            });
            return results;
        }
        return Collections.emptyList();
    }

    /**
     * @param empCode
     * @param categoryFlag
     * @param queryVo
     * @Description: 获取全景档案培训经历列表
     * @return: java.util.List<cn.com.bluemoon.admin.web.personalFile.vo.studyRecord.TrainingVo>
     * @Author: cxb
     * @Date: 2023/6/1 15:46
     */
    private List<TrainingVo> listPanoramaTrainingClassification(String empCode, List<TrainingExperienceCategoryVO> trainingExperienceCategoryVOList, boolean categoryFlag, TrainingQueryVo queryVo) throws Exception {
        if (!categoryFlag) {
            trainingExperienceCategoryVOList = studyRecordSqlMapper.getPanoramaTrainingClassification(empCode);
        }
        if (!CollectionUtils.isEmpty(trainingExperienceCategoryVOList)) {
            List<TrainingVo> results = Collections.synchronizedList(new ArrayList<>());
            trainingExperienceCategoryVOList.parallelStream().forEach(e -> {
                // 判断分类类型是否为考试
                if ("exam".equals(e.getFirstCategoryCode())){
                    List<TrainingVo> examList = studyRecordSqlMapper.listExam(empCode, queryVo);
                    if (!CollectionUtils.isEmpty(examList)){
                        results.addAll(examList);
                    }
                } else {
                    String categoryCode = !"4/999".equals(e.getSecondCategoryCode()) ? e.getSecondCategoryCode() : e.getFirstCategoryCode();
                    List<TrainingVo> trainingVoList = studyRecordSqlMapper.listPanoramaTrainingClassification(empCode, categoryCode, queryVo);
                    if (!CollectionUtils.isEmpty(trainingVoList)){
                        String categoryName = e.getFirstCategoryName() + "/" + e.getSecondCategoryName();
                        trainingVoList.stream().forEach(trainingVo -> {
                            trainingVo.setCategory(categoryName);
                            trainingVo.setSource("全景档案培训经历");
                        });
                    }
                    results.addAll(trainingVoList);
                }
            });
            return results;
        }
        return Collections.emptyList();
    }

    /**
     * @Description: 获取全景档案-培训经历-分类列表
     * @param empCode 雇员编号
     * @return 分类列表
     * @throws Exception 如果出现异常则抛出异常
     * @Author: cxb
     * @Date: 2023/5/31 17:08
     */
    private List<TrainingExperienceCategoryGroupVO> getPanoramaTrainingClassification(String empCode) throws Exception {
        return getTrainingClassification(empCode, "getPanoramaTrainingClassification");
    }

    /**
     * @Description: 获取全景档案-培训经历-授课分类列表
     * @param empCode 雇员编号
     * @return 分类列表
     * @throws Exception 如果出现异常则抛出异常
     * @Author: cxb
     * @Date: 2023/5/31 17:08
     */
    private List<TrainingExperienceCategoryGroupVO> getPanoramaTrainingGiveLessonsClassify(String empCode) throws Exception {
        return getTrainingClassification(empCode, "getPanoramaTrainingGiveLessonsClassify");
    }

    private List<TrainingVo> listPastTraining (String empCode, List<TrainingExperienceCategoryVO> categoryVOList, boolean categoryFlag, TrainingQueryVo queryVo) {
        return (!CollectionUtils.isEmpty(categoryVOList) || !categoryFlag ) ? studyRecordSqlMapper.listPastTraining(empCode, queryVo) : Collections.emptyList();
    }


    /**
     * @Description: 线下培训列表
     * @param empCode
     * @param req
     * @param trainingExperienceCategoryVOList
     * @param categoryFlag
     * @return: java.util.List<cn.com.bluemoon.admin.web.personalFile.vo.studyRecord.TrainingVo>
     * @Author: cxb
     * @Date: 2023/6/2 09:02
     */
    private List<TrainingVo> listTraining(String empCode, TrainingQueryVo req, List<TrainingExperienceCategoryVO> trainingExperienceCategoryVOList, boolean categoryFlag){
        if (!categoryFlag || !CollectionUtils.isEmpty(trainingExperienceCategoryVOList)) {
            StudentFileTrainingExperienceReqVO reqVO = new StudentFileTrainingExperienceReqVO();
            reqVO.setEmpCode(empCode);
            if (StringUtils.isNotBlank(req.getCourseName())) {
                reqVO.setCourseName(req.getCourseName());
            }
            if (req.getStartDate() != null) {
                reqVO.setTrainingBeginDate(java.sql.Date.valueOf(req.getStartDate()));
            }
            if (req.getEndDate() != null) {
                reqVO.setTrainingEndDate(java.sql.Date.valueOf(req.getEndDate()));
            }
            if (!CollectionUtils.isEmpty(trainingExperienceCategoryVOList)) {
                reqVO.setCourseType(trainingExperienceCategoryVOList.stream().map(TrainingExperienceCategoryVO::getFirstCategoryName).collect(Collectors.toList()));
            }
            List<StudentFileTrainingExperienceRespVO> studentFileTrainingExperienceRespVOList = trainStudentInfoService.getStudentFileTrainingExperience(reqVO);
            if (!CollectionUtils.isEmpty(studentFileTrainingExperienceRespVOList)) {
                List<TrainingVo> results = new ArrayList<>();
                studentFileTrainingExperienceRespVOList.stream().forEach(e -> {
                    TrainingVo vo = new TrainingVo();
                    vo.setSource("培训系统-线下培训");
                    vo.setCourseName(e.getCourseName());
                    vo.setCategory(e.getCourseType());
                    if (e.getTrainingDate() != null) {
                        vo.setTrainingDate(DateUtil.getDateString(e.getTrainingDate(), "yyyy-MM-dd"));
                    }
                    results.add(vo);
                });
                return results;
            }
        }
        return Collections.emptyList();
    }

    /**
     * @Description: 线下培训列表
     * @param empCode
     * @param req
     * @param trainingExperienceCategoryVOList
     * @param categoryFlag
     * @return: java.util.List<cn.com.bluemoon.admin.web.personalFile.vo.studyRecord.TrainingVo>
     * @Author: cxb
     * @Date: 2023/6/2 09:02
     */
    private List<TeachingExperienceVo> getStudentFileTeachingExperience(String empCode, TrainingQueryVo req, List<TrainingExperienceCategoryVO> trainingExperienceCategoryVOList, boolean categoryFlag){
        if (!categoryFlag || !CollectionUtils.isEmpty(trainingExperienceCategoryVOList)) {
            StudentFileTrainingExperienceReqVO reqVO = new StudentFileTrainingExperienceReqVO();
            reqVO.setEmpCode(empCode);
            if (StringUtils.isNotBlank(req.getCourseName())) {
                reqVO.setCourseName(req.getCourseName());
            }
            if (req.getStartDate() != null) {
                reqVO.setTrainingBeginDate(java.sql.Date.valueOf(req.getStartDate()));
            }
            if (req.getEndDate() != null) {
                reqVO.setTrainingEndDate(java.sql.Date.valueOf(req.getEndDate()));
            }
            if (!CollectionUtils.isEmpty(trainingExperienceCategoryVOList)) {
                reqVO.setCourseType(trainingExperienceCategoryVOList.stream().map(TrainingExperienceCategoryVO::getFirstCategoryName).collect(Collectors.toList()));
            }
            List<StudentFileTeachingExperienceRespVO> list = trainStudentInfoService.getStudentFileTeachingExperience(reqVO);
            if (!CollectionUtils.isEmpty(list)) {
                List<TeachingExperienceVo> results = new ArrayList<>();
                list.stream().forEach(e -> {
                    TeachingExperienceVo vo = new TeachingExperienceVo();
                    vo.setSource("培训系统-线下授课");
                    vo.setCourseName(e.getCourseName());
                    vo.setCategory(e.getCourseType());
                    vo.setTeachingHours(e.getTeachingLength());
                    vo.setContentScore(e.getContentScore());
                    vo.setTeachingScore(e.getTrainingScore());
                    vo.setTotalTrainingScore(e.getLecturerScore());
                    vo.setParticipantsNum(e.getAttendTrainingNum());
                    if (e.getTrainingDate() != null) {
                        vo.setTrainingDate(DateUtil.getDateString(e.getTrainingDate(), "yyyy-MM-dd"));
                    }
                    results.add(vo);
                });
                return results;
            }
        }
        return Collections.emptyList();
    }

    /**
     * 获取培训经历分类列表
     *
     * @param empCode     雇员编号
     * @param methodName 方法名
     * @return 分类列表
     * @throws Exception 如果出现异常则抛出异常
     */
    private List<TrainingExperienceCategoryGroupVO> getTrainingClassification(String empCode, String methodName) throws Exception {
        List<TrainingExperienceCategoryVO> trainingExperienceCategoryVOList = null;
        String sourceType = null;
        switch (methodName) {
            case "getPanoramaTrainingClassification":
                trainingExperienceCategoryVOList = studyRecordSqlMapper.getPanoramaTrainingClassification(empCode);
                sourceType = "全景档案培训经历";
                break;
            case "getPanoramaTrainingGiveLessonsClassify":
                trainingExperienceCategoryVOList = studyRecordSqlMapper.getPanoramaTrainingGiveLessonsClassify(empCode);
                sourceType = "全景档案授课经历";
                break;
            default:
                throw new IllegalArgumentException("Invalid methodName: " + methodName);
        }
        if (CollectionUtils.isEmpty(trainingExperienceCategoryVOList)) {
            return Collections.emptyList();
        }
        Map<String, List<TrainingExperienceCategoryVO>> tExpCategoryMap =
                trainingExperienceCategoryVOList.stream().collect(Collectors.groupingBy(TrainingExperienceCategoryVO::getFirstCategoryCode));
        List<TrainingExperienceCategoryGroupVO> results = new ArrayList<>();
        for (Map.Entry<String, List<TrainingExperienceCategoryVO>> entry : tExpCategoryMap.entrySet()) {
            String categoryCode = entry.getKey();
            List<TrainingExperienceCategoryVO> trainingExperienceCategoryList = entry.getValue();
            TrainingExperienceCategoryGroupVO vo = new TrainingExperienceCategoryGroupVO();
            vo.setCategoryCode(categoryCode);
            vo.setCategoryName(trainingExperienceCategoryList.get(0).getFirstCategoryName());
            vo.setSourceType(sourceType);
            List<TrainingExperienceCategoryGroupVO> childs = new ArrayList<>();
            for (TrainingExperienceCategoryVO trainingExperienceCategoryVO : trainingExperienceCategoryList) {
                if (StringUtils.isNotBlank(trainingExperienceCategoryVO.getSecondCategoryCode())) {
                    TrainingExperienceCategoryGroupVO child = new TrainingExperienceCategoryGroupVO();
                    child.setCategoryCode(trainingExperienceCategoryVO.getSecondCategoryCode());
                    child.setCategoryName(trainingExperienceCategoryVO.getSecondCategoryName());
                    child.setParentCategoryCode(vo.getCategoryCode());
                    child.setSourceType(sourceType);
                    childs.add(child);
                }
            }
            vo.setChilds(childs);
            results.add(vo);
        }
        return results;
    }


    /**
     * @Description: 获取线上培训分类
     * @param
     * @return: java.util.List<cn.com.bluemoon.admin.web.personalFile.vo.studyRecord.TrainingExperienceCategoryGroupVO>
     * @Author: cxb
     * @Date: 2023/6/1 10:02
     */
    private List<TrainingExperienceCategoryGroupVO> getTrainingTypeList(String empCode){
        CourseTypeListReq req = new CourseTypeListReq();
        req.setUserCode(Integer.valueOf(empCode));
        List<CourseTypeResp> courseTypeRespList = ihdTrainingDubboService.getTrainingTypeList(req);
        if (!CollectionUtils.isEmpty(courseTypeRespList)){
            List<TrainingExperienceCategoryGroupVO> results = new ArrayList<>();
            courseTypeRespList.stream().forEach(e -> {
                TrainingExperienceCategoryGroupVO vo = new TrainingExperienceCategoryGroupVO();
                vo.setCategoryCode(e.getCourseBaseCode());
                vo.setCategoryName(e.getCourseBaseName());
                vo.setSourceType("培训系统-线上培训");
                List<TrainingExperienceCategoryGroupVO> childs = new ArrayList<>();
                List<CourseTypeResp> courseTypeChilds = e.getChildren();
                if (!CollectionUtils.isEmpty(courseTypeChilds)){
                    courseTypeChilds.stream().forEach(courseTypeChild -> {
                        TrainingExperienceCategoryGroupVO child = new TrainingExperienceCategoryGroupVO();
                        child.setCategoryCode(courseTypeChild.getCourseBaseCode());
                        child.setCategoryName(courseTypeChild.getCourseBaseName());
                        child.setParentCategoryCode(vo.getCategoryCode());
                        child.setSourceType("培训系统-线上培训");
                        childs.add(child);
                    });
                }
                vo.setChilds(childs);
                results.add(vo);
            });
            return results;
        }
        return Collections.EMPTY_LIST;
    }

    private List<TrainingVo> getCourseInfoList(String empCode, TrainingQueryVo req, List<TrainingExperienceCategoryVO> trainingExperienceCategoryVOList, boolean categoryFlag){
        if (!categoryFlag || !CollectionUtils.isEmpty(trainingExperienceCategoryVOList)) {
            CourseSuiteListReq courseSuiteListReq = new CourseSuiteListReq();
            courseSuiteListReq.setUserCode(Integer.valueOf(empCode));
            if (StringUtils.isNotBlank(req.getCourseName())){
                courseSuiteListReq.setCourseName(req.getCourseName());
            }
            if (req.getStartDate() != null) {
                courseSuiteListReq.setTrainingStartDate(java.sql.Date.valueOf(req.getStartDate()));
            }
            if (req.getEndDate() != null) {
                courseSuiteListReq.setTrainingEndDate(java.sql.Date.valueOf(req.getEndDate()));
            }
            if (!CollectionUtils.isEmpty(trainingExperienceCategoryVOList)) {
                List<String> categoryCodeList = trainingExperienceCategoryVOList.stream().map(e -> {
                    if (StringUtils.isBlank(e.getSecondCategoryName())) {
                        return e.getFirstCategoryName();
                    }
                    return e.getFirstCategoryName() + "/" + e.getSecondCategoryName();
                }).collect(Collectors.toList());
                courseSuiteListReq.setTrainingTypeCodes(Joiner.on(",").join(categoryCodeList));
            }
            List<CourseListResp> courseListRespList = ihdTrainingDubboService.getCourseInfoList(courseSuiteListReq);
            if (!CollectionUtils.isEmpty(courseListRespList)) {
                List<TrainingVo> results = new ArrayList<>();
                courseListRespList.stream().forEach(e -> {
                    TrainingVo vo = new TrainingVo();
                    vo.setSource("培训系统-线上培训");
                    vo.setCourseName(e.getTrainingCourseName());
                    vo.setCategory(e.getTrainingTypeName());
                    if (e.getTrainingDate() != null) {
                        vo.setTrainingDate(DateUtil.getDateString(e.getTrainingDate(), "yyyy-MM-dd"));
                    }
                    vo.setStudyHours(e.getStudyTime());
                    vo.setExamScore(e.getScore());
                    results.add(vo);
                });
                return results;
            }
        }
        return Collections.emptyList();
    }

    /**
     * @Description: 获取线下培训分类列表
     * @param empCode type(1:培训;2:授课;)
     * @return: java.util.List<cn.com.bluemoon.admin.web.personalFile.vo.studyRecord.TrainingExperienceCategoryGroupVO>
     * @Author: cxb
     * @Date: 2023/6/1 09:50
     */
    private List<TrainingExperienceCategoryGroupVO> getStudentFileClassification (String empCode, Integer type){
        String sourceType = type == 1 ? "培训系统-线下培训" : "培训系统-线下授课";
        StudentFileClassificationReqVO reqVO = new StudentFileClassificationReqVO();
        reqVO.setEmpCode(empCode);
        reqVO.setType(type);
        List<StudentFileClassificationRespVO> studentFileClassificationRespVOList = trainStudentInfoService.getStudentFileClassification(reqVO);
        if (!CollectionUtils.isEmpty(studentFileClassificationRespVOList)) {
            List<TrainingExperienceCategoryGroupVO> results = new ArrayList<>();
            studentFileClassificationRespVOList.stream().forEach(e -> {
                TrainingExperienceCategoryGroupVO vo = new TrainingExperienceCategoryGroupVO();
                vo.setCategoryCode(e.getCourseCode());
                vo.setCategoryName(e.getCourseType());
                vo.setSourceType(sourceType);
                results.add(vo);
            });
            return results;
        }
        return Collections.EMPTY_LIST;
    }


    /**
     * @Description: 获取过往经历分类列表
     * @param
     * @return: java.util.List<cn.com.bluemoon.admin.web.personalFile.vo.studyRecord.TrainingExperienceCategoryGroupVO>
     * @Author: cxb
     * @Date: 2023/6/1 09:26
     */
    private List<TrainingExperienceCategoryGroupVO> getTrainingExperienceCategories(){
        TrainingExperienceCategoryGroupVO vo = new TrainingExperienceCategoryGroupVO();
        vo.setCategoryCode("4/999");
        vo.setCategoryName("其他");
        vo.setSourceType("往培训经历");
        TrainingExperienceCategoryGroupVO child = new TrainingExperienceCategoryGroupVO();
        child.setCategoryCode("pastTraining");
        child.setCategoryName("过往培训");
        child.setSourceType("往培训经历");
        vo.setChilds(Stream.of(child).collect(Collectors.toList()));
        return Stream.of(vo).collect(Collectors.toList());
    }
}
