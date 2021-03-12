package com.woniu.yujiaweb.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.woniu.yujiaweb.domain.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.woniu.yujiaweb.vo.PageGymVo;
import com.woniu.yujiaweb.vo.PageUserVo;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author qk
 * @since 2021-03-06
 */
public interface UserService extends IService<User> {


    //查询所有的学员信息
    public Page<PageUserVo> findAllUser(PageUserVo pageUserVo);

    //根据学员id删除学员信息
    public Boolean delStudent(Integer userId);

    //根据学院id修改学员信息
    public Boolean updateStudent(User user);

    //条件分页查询场馆的信息
    public Page<PageGymVo> findAllGym(PageGymVo pageGymVo);

    //修改场馆的信息
    public boolean updateGym(PageGymVo pageGymVo);

    //查询所有的管理员信息
    public Page<PageUserVo> findAllAdmin(PageUserVo pageUserVo);


}
