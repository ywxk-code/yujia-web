package com.woniu.yujiaweb.controller;


import com.aliyuncs.exceptions.ClientException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.woniu.yujiaweb.domain.Permission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.woniu.yujiaweb.domain.User;
import com.woniu.yujiaweb.domain.UserInfo;
import com.woniu.yujiaweb.dto.Result;
import com.woniu.yujiaweb.dto.StatusCode;
import com.woniu.yujiaweb.mapper.UserMapper;
import com.woniu.yujiaweb.service.UserInfoService;
import com.woniu.yujiaweb.service.UserService;
import com.woniu.yujiaweb.service.impl.UserServiceImpl;
import com.woniu.yujiaweb.util.AliyunSmsUtils;
import com.woniu.yujiaweb.util.JWTUtil;
import com.woniu.yujiaweb.util.MailUtils;
import com.woniu.yujiaweb.util.SaltUtil;
import com.woniu.yujiaweb.vo.PageGymVo;
import com.woniu.yujiaweb.vo.PageUserVo;
import com.woniu.yujiaweb.vo.UserVO;
import com.woniu.yujiaweb.vo.YuJiaVO;
import io.swagger.annotations.*;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.crypto.hash.Md5Hash;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

import org.springframework.stereotype.Controller;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.servlet.http.HttpSession;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * <p>
 *  ???????????????
 * </p>
 *
 * @author yym
 * @since 2021-03-08
 */
@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private RedisTemplate<String,Object> redisTemplate;
    @Resource
    private UserService userService;
    @Resource
    private UserInfoService userInfoService;
    @PostMapping ("/register")
    //@ApiOperation?????????????????????????????????????????????
    @ApiOperation(value = "????????????",notes = "<span style='color:red;'>???????????????????????????</span>")
    public Result register(@RequestBody UserVO userVO){
        //?????????????????????????????????
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username",userVO.getUsername());
        User userDB = userService.getOne(queryWrapper);
        //??????????????????
        if (ObjectUtils.isEmpty(userDB)) {
            //????????????
            userDB=new User();
            SaltUtil saltUtil = new SaltUtil(8);
            String salt = saltUtil.getSalt();
            Md5Hash hash = new Md5Hash(userVO.getPassword(), salt,2048);
            userDB.setUsername(userVO.getUsername());
            userDB.setPassword(hash.toHex());
            userDB.setSalt(salt);
            //????????????
            userDB.setGmtCreate(new Date());
            if(userVO.getContact().contains(".com")){
                //????????????
                String email = userVO.getContact();
                userDB.setEmail(email);
            }else {
                //????????????
                String tel = userVO.getContact();
                userDB.setTel(tel);
            }
            //????????????????????????????????????
            if (userVO.getAuthCode().equals(redisTemplate.opsForValue().get("authCode"))){
                //????????????????????????t_user
                userService.save(userDB);
            }else {
                return new Result(false, StatusCode.AUTHCODE,"???????????????");
            }
            //???????????????????????????????????????????????????
            redisTemplate.opsForValue().set("nickname",userVO.getNickname());
            redisTemplate.opsForValue().set("bankCard",userVO.getBankCard());
            redisTemplate.opsForValue().set("sex",userVO.getSex());
            //???t_user_role????????????(userVO.getRadio())
            redisTemplate.opsForValue().set("radio",userVO.getRadio());
            return new Result(true, StatusCode.OK,"????????????");
        }else {
            return new Result(false, StatusCode.ACCOUNTEXISTS,"???????????????");
        }
    }
    @PostMapping ("/login")
    //@ApiOperation?????????????????????????????????????????????
    @ApiOperation(value = "????????????",notes = "<span style='color:red;'>?????????????????????????????????</span>")
    //@ApiImplicitParams????????????????????????
    @ApiResponses({
            @ApiResponse(code =20002,message = "????????????"),
            @ApiResponse(code=20003,message = "???????????????")
    })
    @ApiImplicitParams({
            //dataType:????????????
            //paramType:?????????????????????     path->?????????????????????query->??????????body->ajax??????
            @ApiImplicitParam(name = "userVO",value = "?????????????????????????????????",dataType = "UserVO",example = "{username:'tom',password:'1234'}"),

    })
    public Result login(@RequestBody UserVO userVO){
        System.out.println("??????login");
        UsernamePasswordToken token = new UsernamePasswordToken(userVO.getUsername(), userVO.getPassword());
        Subject subject = SecurityUtils.getSubject();
        subject.login(token);
        //??????????????????????????????id
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username",userVO.getUsername());
        User userDB = userService.getOne(queryWrapper);
        if(!ObjectUtils.isEmpty(userDB)){
            //????????????
            redisTemplate.opsForValue().set("username",userVO.getUsername());
            //???????????????
            String radio = (String) redisTemplate.opsForValue().get("radio");
            //userDB.getId()????????????uid
            userService.saveUserAndRole(Integer.toString(userDB.getId()),radio);
            //???????????????????????????????????????????????????t_user_info????????????
            QueryWrapper<UserInfo> wrapper = new QueryWrapper<>();
            //????????????????????????id
            wrapper.eq("username",userVO.getUsername());
            UserInfo userInfo = userInfoService.getOne(wrapper);
            if(ObjectUtils.isEmpty(userInfo)){
                //????????????????????????
                String nickname = (String) redisTemplate.opsForValue().get("nickname");
                String bankCard = (String) redisTemplate.opsForValue().get("bankCard");
                String sex = (String) redisTemplate.opsForValue().get("sex");
                //???????????????????????????
                UserInfo userInfo1 = new UserInfo();
                userInfo1.setUsername(userVO.getUsername());
                userInfo1.setUid(userDB.getId());
                userInfo1.setNickname(nickname);
                userInfo1.setBankCard(bankCard);
                userInfo1.setSex(sex);
                userInfoService.save(userInfo1);
            }
        }
//      ??????jwt,??????????????????????????????????????????session???
        HashMap<String, String> map = new HashMap<>();
        map.put("username",userVO.getUsername());
        String jwtToken = JWTUtil.createToken(map);
        JWTUtil.getUsernames().add(userVO.getUsername());
        System.out.println(jwtToken);
        System.out.println("??????login");

        return new Result(true, StatusCode.OK,"????????????",jwtToken);
    }
    @PostMapping ("/getAuthCode")
    //@ApiOperation?????????????????????????????????????????????
    @ApiOperation(value = "???????????????",notes = "<span style='color:red;'>?????????????????????????????????</span>")
    public Result getAuthCode(@RequestBody UserVO userVO){
        //??????????????????????????????
        if(userVO.getContact().contains(".com")){
            //????????????
            try {
                MailUtils.newcode="";
                MailUtils.setNewcode();
                String code = MailUtils.getNewcode();
                //?????????????????????
                redisTemplate.opsForValue().set("authCode",code);
                MailUtils.sendMail(userVO.getContact(),"?????????",code);

            } catch (MessagingException e) {
                e.printStackTrace();
            }
        }else{
            //????????????
            try {
                AliyunSmsUtils.newcode="";
                AliyunSmsUtils.setNewcode();
                String code = AliyunSmsUtils.getNewcode();
                //?????????????????????
                redisTemplate.opsForValue().set("authCode",code);
                AliyunSmsUtils.sendSms(userVO.getContact(),code);
            } catch (ClientException e) {
                e.printStackTrace();
            }
        }
        return new Result(true,StatusCode.OK,"?????????????????????");
    }
    @PostMapping ("/getpassword")
    //@ApiOperation?????????????????????????????????????????????
    @ApiOperation(value = "????????????",notes = "<span style='color:red;'>???????????????????????????</span>")
    public Result getpassword(@RequestBody UserVO userVO){
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username",userVO.getUsername());
        User userDB = userService.getOne(queryWrapper);
        //????????????????????????
        if (!ObjectUtils.isEmpty(userDB)) {
            //????????????
            userDB=new User();
            SaltUtil saltUtil = new SaltUtil(8);
            String salt = saltUtil.getSalt();
            Md5Hash hash = new Md5Hash(userVO.getPassword(), salt,2048);
            userDB.setPassword(hash.toHex());
            userDB.setSalt(salt);
            //????????????
            userDB.setGmtModifified(new Date());
            if(userVO.getContact().contains(".com")){
                //????????????
                try {
                    MailUtils.newcode="";
                    MailUtils.setNewcode();
                    String code = MailUtils.getNewcode();
                    //?????????????????????
                    redisTemplate.opsForValue().set("newAuthCode",code);
                    MailUtils.sendMail(userVO.getContact(),"?????????",code);

                } catch (MessagingException e) {
                    e.printStackTrace();
                }
                userDB.setEmail(userVO.getContact());
            }else {
                //????????????
                try {
                    AliyunSmsUtils.newcode="";
                    AliyunSmsUtils.setNewcode();
                    String code = AliyunSmsUtils.getNewcode();
                    //?????????????????????
                    redisTemplate.opsForValue().set("newAuthCode",code);
                    AliyunSmsUtils.sendSms(userVO.getContact(),code);
                } catch (ClientException e) {
                    e.printStackTrace();
                }
                userDB.setTel(userVO.getContact());
            }
            //?????????????????????
            UpdateWrapper<User> wrapper = new UpdateWrapper<>();
            wrapper.eq("username",userVO.getUsername());
            userService.update(userDB,wrapper);
            //????????????????????????????????????
//            System.out.println("???????????????"+userVO.getAuthCode());
//            System.out.println("???????????????"+redisTemplate.opsForValue().get("newAuthCode"));
//            if (userVO.getAuthCode().equals(redisTemplate.opsForValue().get("newAuthCode"))){
//                //???????????????????????????
//                System.out.println("?????????????????????");
//                UpdateWrapper<User> wrapper = new UpdateWrapper<>();
//                wrapper.eq("username",userVO.getUsername());
//                userService.update(userDB,wrapper);
//            }else {
//                return new Result(false, StatusCode.AUTHCODE,"???????????????");
//            }
        }
        return new Result(true, StatusCode.OK,"???????????????????????????????????????");
    }
    //@ApiOperation?????????????????????????????????????????????
    @ApiOperation(value = "??????????????????",notes = "<span style='color:red;'>????????????????????????</span>")
    //@ApiImplicitParams????????????????????????
    @ApiResponses({
            @ApiResponse(code =20000,message = "????????????????????????"),

    })
    @ApiImplicitParams({
            //dataType:????????????
            //paramType:?????????????????????     path->?????????????????????query->??????????body->ajax??????
            @ApiImplicitParam(name = "userVO",value = "?????????????????????????????????",dataType = "UserVO",example = "{username:'tom',password:'xxx'}"),

    })
    @RequestMapping("findManue")
    @ResponseBody
    public Result findManue(@RequestBody UserVO userVO){
        System.out.println("??????find"+userVO.getUsername());
        List<Permission> rootManue = userService.findManue(userVO.getUsername());
        return new Result(true, StatusCode.OK,"????????????????????????",rootManue);

    }
    @ApiOperation(value = "??????????????????",notes = "<span style='color:red;'>????????????????????????</span>")
    //@ApiImplicitParams????????????????????????
    @ApiResponses({
            @ApiResponse(code =20000,message = "????????????????????????"),

    })
    @ApiImplicitParams({
            //dataType:????????????
            //paramType:?????????????????????     path->?????????????????????query->??????????body->ajax??????
            @ApiImplicitParam(name = "userVO",value = "?????????????????????????????????",dataType = "UserVO",example = "{username:'tom',password:'xxx'}"),

    })
    @RequestMapping("findManue2")
    @ResponseBody
    public Result findManue2(@RequestBody UserVO userVO){
        System.out.println("??????find"+userVO.getUsername());
        List<Permission> rootManue = userService.findManue2(userVO.getUsername());
        return new Result(true, StatusCode.OK," ",rootManue);
    }

    @ApiOperation(value = "????????????",notes = "<span style='color:red;'>??????????????????</span>")
    //@ApiImplicitParams????????????????????????
    @ApiResponses({
            @ApiResponse(code =20000,message = "????????????"),

    })
    @ApiImplicitParams({
            //dataType:????????????
            //paramType:?????????????????????     path->?????????????????????query->??????????body->ajax??????
            @ApiImplicitParam(name = "userVO",value = "?????????????????????????????????",dataType = "UserVO",example = "{username:'tom',password:'xxx'}"),

    })
    @GetMapping("/findPlace")
    public Result findPlace(){
        List<User> place = userService.findPlace();
        ArrayList<String> places = new ArrayList<>();
        place.forEach(p->{
            places.add(p.getUsername());
        });
        return new Result(true, StatusCode.OK,"??????????????????",places);
    }

    @PostMapping("/findYPlace")
    @ResponseBody
    public Result findYPlace(@RequestBody YuJiaVO yuJiaVO){
        List<User> yPlace = userService.findYPlace(yuJiaVO.getYid());
        return new Result(true, StatusCode.OK,"?????????????????????????????????",yPlace);
    }

    //???????????????????????????
    @GetMapping("/findAllStudent")
    public Result findAllStudent(PageUserVo pageUserVo){
        //??????????????????????????????id(??????????????????????????????????????????)
        System.out.println("?????????" + pageUserVo.getCurrent());
        Integer rid = 1;
        Page<PageUserVo> allStudent = userService.findAllUser(pageUserVo);
        return new Result(true, StatusCode.OK,"??????????????????????????????",allStudent);
    }
    //???????????????????????????
    @GetMapping("/findAllCoach")
    public Result findAllCoach(PageUserVo pageUserVo){
        //??????????????????????????????id(??????????????????????????????????????????)
        Integer rid = 2;
        Page<PageUserVo> allCoach = userService.findAllUser(pageUserVo);
        return new Result(true, StatusCode.OK,"??????????????????????????????",allCoach);
    }
    //???????????????????????????
    @GetMapping("/findAllGym")
    public Result findAllGym(PageGymVo pageGymVo){
        //??????????????????????????????id(??????????????????????????????????????????)
        Integer rid = 3;
        Page<PageGymVo> allGym = userService.findAllGym(pageGymVo);
        return new Result(true, StatusCode.OK,"??????????????????????????????",allGym);
    }

    //?????????????????????
    @PostMapping("/delStudent/{id}")
    public Result delStudent(@PathVariable Integer id){
        System.out.println("??????id" + id);
        Boolean b = userService.delStudent(id);
        if(b){
            return new Result(true,StatusCode.OK,"????????????");
        }
        return new Result(false,StatusCode.ERROR,"????????????");
    }

    //?????????????????????
    @PostMapping("/updateStudent")
    public Result updateStudent(@RequestBody User user){
        System.out.println("??????????????????" + user);
        Boolean f = userService.updateStudent(user);
        if(f){
            return new Result(true,StatusCode.OK,"????????????");
        }else{
            return new Result(false,StatusCode.ERROR,"????????????");
        }
    }

    //?????????????????????
    @PostMapping("updateGym")
    public Result updateGym(@RequestBody PageGymVo pageGymVo){
        return new Result(true,StatusCode.OK,"????????????");
    }

    //??????????????????????????????
    @GetMapping("/findAllAdmin")
    public Result findAllAdmin(PageUserVo pageUserVo){
        //??????????????????????????????id(??????????????????????????????????????????)
        Integer rid = 4;
        Page<PageUserVo> allAdmin = userService.findAllAdmin(pageUserVo);
        return new Result(true, StatusCode.OK,"??????????????????????????????",allAdmin);
    }

    @PostMapping("/logout")
    public Result show(@RequestBody UserVO userVO){
        System.out.println("??????logout");
        redisTemplate.delete(userVO.getUsername());
        return new Result(true, StatusCode.OK,"????????????");

    }
}

