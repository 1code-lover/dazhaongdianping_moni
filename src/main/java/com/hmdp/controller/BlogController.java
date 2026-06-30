package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 博客/探店控制器
 * 处理用户博客相关的 HTTP 请求，包括博客发布、查询、点赞、Feed 流等功能
 * 接口路径：/blog
 * 包含关注 Feed 流推送、博客点赞统计等功能
 *
 * @author ethan
 * @date 2026-06-14
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    /**
     * 发布博客
     * 用户发布新的探店博客内容
     *
     * @param blog 博客对象（包含标题、内容、图片等）
     * @return 发布结果
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 博客点赞
     * 为指定博客点赞，使用 Redis Set 存储点赞用户
     * 同一用户可以取消点赞
     *
     * @param id 博客ID
     * @return 点赞结果
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    /**
     * 查询我的博客
     * 分页查询当前登录用户发布的所有博客
     *
     * @param current 页码（从1开始，默认1）
     * @return 用户的博客列表
     */
    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 查询热门博客
     * 分页查询点赞数最多的热门博客
     *
     * @param current 页码（从1开始，默认1）
     * @return 热门博客列表
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /**
     * 查询博客详情
     * 根据博客ID查询博客内容、点赞数、评论数等详细信息
     *
     * @param id 博客ID
     * @return 博客详情
     */
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    /**
     * 查询博客点赞用户列表
     * 获取为指定博客点赞的用户列表
     *
     * @param id 博客ID
     * @return 点赞用户列表
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }

    /**
     * 查询指定用户的博客
     * 分页查询某个用户发布的所有博客
     *
     * @param current 页码（从1开始，默认1）
     * @param id 用户ID
     * @return 用户的博客列表
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 关注 Feed 流
     * 分页查询当前用户关注的人发布的博客
     * 采用滚动分页方式，通过 lastId 和 offset 实现高效滚动查询
     *
     * @param max 上一次查询的最后一个博客ID（用于滚动分页）
     * @param offset 偏移量（用于处理同一时间发布的多个博客）
     * @return Feed 流博客列表
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max, @RequestParam(value = "offset", defaultValue = "0") Integer offset){
        return blogService.queryBlogOfFollow(max, offset);
    }
}
