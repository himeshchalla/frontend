define([
    'common/modules/commercial/commercial-features',
    'common/modules/commercial/targeting-tool',
    'common/utils/$',
    'common/utils/config',
    'common/utils/cookies',
    'common/utils/element-inview',
    'common/utils/fastdom-promise',
    'common/utils/mediator',
    'common/utils/storage',
], function (commercialFeatures,
             targetingTool,
             $,
             config,
             cookies,
             ElementInView,
             fastdom,
             mediator,
             storage) {

    var membershipURL = 'https://membership.theguardian.com/supporter';
    var contributionsURL = 'https://contribute.theguardian.com';

    var membershipCampaignPrefix = 'gdnwb_copts_mem';
    var contributionsCampaignPrefix = 'co_global';

    var viewKey = 'gu.contributions.views';
    var viewLog = storage.local.get(viewKey) || [];

    var lastContributionDate = cookies.get('gu.contributions.contrib-timestamp');

    /**
     * How many times the user can see the Epic, e.g. 6 times within 7 days.
     * @type {{days: number, count: number}}
     */
    var maxViews = {
        days: 7,
        count: 6
    };

    /**
     * Log that the user has seen an Epic test so we can limit how many times they see it.
     *
     * @param testId
     */
    function logView(testId) {
        viewLog.push({
            date: new Date().getTime(),
            testId: testId
        });

        storage.local.set(viewKey, viewLog);
    }

    function canShow() {
        var maxDays = maxViews.days * 1000 * 60 * 60 * 24;
        var now = new Date().getTime();

        return viewLog.filter(function (view) {
            return view.date > (now - maxDays);
        }).length <= maxViews.count;
    }

    function daysSince(date) {
        var oneDay = 24 * 60 * 60 * 1000;

        try {
            var ms = Date.parse(date);

            if (isNaN(ms)) return Infinity;
            return (new Date() - ms) / oneDay;
        } catch(e) {
            return Infinity;
        }
    }

    function ContributionsABTest(options) {
        this.id = options.id;
        this.start = options.start;
        this.expiry = options.expiry;
        this.author = options.author;
        this.idealOutcome = options.idealOutcome;
        this.campaignId = options.campaignId;
        this.description = options.description;
        this.showForSensitive = options.showForSensitive || false;
        this.audience = options.audience;
        this.audienceOffset = options.audienceOffset;
        this.successMeasure = options.successMeasure;
        this.audienceCriteria = options.audienceCriteria;
        this.dataLinkNames = options.dataLinkNames || '';

        this.contributeURL = options.contributeURL || this.makeURL(contributionsURL, contributionsCampaignPrefix);
        this.membershipURL = options.membershipURL || this.makeURL(membershipURL, membershipCampaignPrefix);

        this.insertEvent = this.makeEvent('insert');
        this.viewEvent = this.makeEvent('view');

        /**
         * Provides a default `canRun` function with typical rules for Contributions messages. If your test provides
         * its own `canRun` option, it will be included in the check.
         *
         * You can alternatively use the `overrideCanRun` option, which, if true, will only use the `canRun`
         * option provided and ignore the rules here.
         *
         * @type {Function}
         */
        this.canRun = options.overrideCanRun ? options.canRun : (function () {
            var testCanRun = (typeof options.canRun === 'function') ? options.canRun() : true;
            var okToAsk = daysSince(lastContributionDate) >= 90 && canShow();
            var tagsMatch = options.useTargetingTool ? targetingTool.isAbTestTargeted(this) : true;
            var worksWellWithPageTemplate = (config.page.contentType === 'Article') && !config.page.isMinuteArticle;

            return okToAsk && tagsMatch && testCanRun && commercialFeatures.canReasonablyAskForMoney && worksWellWithPageTemplate;
        }).bind(this);

        this.variants = options.variants.map(function (variant) {
            return new ContributionsABTestVariant(variant, this);
        }.bind(this));
    }

    ContributionsABTest.prototype.makeEvent = function (event) {
        return this.id + ':' + event;
    };

    ContributionsABTest.prototype.makeURL = function (base, campaignCodePrefix) {
        return base + '?' + campaignCodePrefix + '_' + this.campaignId;
    };

    function ContributionsABTestVariant(options, test) {
        this.id = options.id;
        this.test = function () {
            var component = $.create(options.template(test.contributeURL, test.membershipURL));

            return options.test(function () {
                return fastdom.write(function () {
                    var sibling = $(options.insertBeforeSelector);

                    if (sibling.length > 0) {
                        component.insertBefore(sibling);
                        mediator.emit(test.insertEvent, component);

                        component.each(function (element) {
                            // top offset of 18 ensures view only counts when half of element is on screen
                            var elementInView = ElementInView(element, window, { top: 18 });

                            elementInView.on('firstview', function () {
                                logView(test.id);
                                mediator.emit(test.viewEvent);
                            });
                        });
                    }
                });
            });
        };

        this.registerListener('impression', 'impressionOnInsert', test.insertEvent, options);
        this.registerListener('success', 'successOnView', test.viewEvent, options);
    }

    ContributionsABTestVariant.prototype.registerListener = function (type, defaultFlag, event, options) {
        if (options[type]) this[type] = options[type];
        else if (options[defaultFlag]) {
            this[type] = (function (track) {
                return mediator.on(event, track);
            }).bind(this);
        }
    };

    return {
        makeABTest: function (test) {
            // this is so it can be instantiated with `new` later
            return function () {
                return new ContributionsABTest(test);
            };
        },

        inAlwaysAskTest: function () {
            var participations = storage.local.get('gu.ab.participations') || {};
            return ('ContributionsEpicAlwaysAskStrategy' in participations);
        }
    };
});